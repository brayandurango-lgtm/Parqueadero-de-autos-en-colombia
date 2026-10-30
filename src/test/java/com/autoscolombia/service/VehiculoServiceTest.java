package com.autoscolombia.service;

import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.Vehiculo;
import com.autoscolombia.domain.service.VehiculoService;
import com.autoscolombia.infrastructure.repository.VehiculoRepository;
import com.autoscolombia.presentation.dto.VehiculoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehiculoServiceTest {

    @Mock
    private VehiculoRepository vehiculoRepository;

    @InjectMocks
    private VehiculoService vehiculoService;

    private VehiculoDTO vehiculoDTO;
    private Vehiculo vehiculo;

    @BeforeEach
    void setUp() {
        vehiculoDTO = VehiculoDTO.builder()
                .placa("ABC123")
                .marca("Toyota")
                .modelo("Corolla")
                .color("Rojo")
                .tipo(TipoVehiculo.CARRO)
                .propietarioDocumento("12345678")
                .propietarioNombre("Juan Pérez")
                .propietarioTelefono("3001234567")
                .build();

        vehiculo = Vehiculo.builder()
                .id(1L)
                .placa("ABC123")
                .marca("Toyota")
                .modelo("Corolla")
                .color("Rojo")
                .tipo(TipoVehiculo.CARRO)
                .propietarioDocumento("12345678")
                .propietarioNombre("Juan Pérez")
                .propietarioTelefono("3001234567")
                .build();
    }

    @Test
    void testCrearOActualizar_NuevoVehiculo() {
        when(vehiculoRepository.findByPlaca(anyString())).thenReturn(Optional.empty());
        when(vehiculoRepository.save(any(Vehiculo.class))).thenReturn(vehiculo);

        Vehiculo resultado = vehiculoService.crearOActualizar(vehiculoDTO);

        assertNotNull(resultado);
        assertEquals("ABC123", resultado.getPlaca());
        verify(vehiculoRepository, times(1)).save(any(Vehiculo.class));
    }

    @Test
    void testCrearOActualizar_VehiculoExistente() {
        when(vehiculoRepository.findByPlaca(anyString())).thenReturn(Optional.of(vehiculo));
        when(vehiculoRepository.save(any(Vehiculo.class))).thenReturn(vehiculo);

        Vehiculo resultado = vehiculoService.crearOActualizar(vehiculoDTO);

        assertNotNull(resultado);
        verify(vehiculoRepository, times(1)).save(any(Vehiculo.class));
    }

    @Test
    void testBuscarPorPlaca_Encontrado() {
        when(vehiculoRepository.findByPlaca(anyString())).thenReturn(Optional.of(vehiculo));

        Optional<Vehiculo> resultado = vehiculoService.buscarPorPlaca("ABC123");

        assertTrue(resultado.isPresent());
        assertEquals("ABC123", resultado.get().getPlaca());
    }

    @Test
    void testBuscarPorPlaca_NoEncontrado() {
        when(vehiculoRepository.findByPlaca(anyString())).thenReturn(Optional.empty());

        Optional<Vehiculo> resultado = vehiculoService.buscarPorPlaca("XYZ999");

        assertFalse(resultado.isPresent());
    }

    @Test
    void testBuscar_SinCriterio() {
        when(vehiculoRepository.findAll()).thenReturn(Arrays.asList(vehiculo));

        List<VehiculoDTO> resultado = vehiculoService.buscar(null, null, null);

        assertFalse(resultado.isEmpty());
        assertEquals(1, resultado.size());
    }

    @Test
    void testBuscar_ConCriterio() {
        when(vehiculoRepository.findByPlacaContainingIgnoreCaseOrPropietarioNombreContainingIgnoreCaseOrPropietarioDocumentoContaining(
                anyString(), anyString(), anyString())).thenReturn(Arrays.asList(vehiculo));

        List<VehiculoDTO> resultado = vehiculoService.buscar("ABC", null, null);

        assertFalse(resultado.isEmpty());
    }

    @Test
    void testBuscar_ConTipo() {
        when(vehiculoRepository.findAll()).thenReturn(Arrays.asList(vehiculo));

        List<VehiculoDTO> resultado = vehiculoService.buscar(null, TipoVehiculo.CARRO, null);

        assertFalse(resultado.isEmpty());
        assertEquals(TipoVehiculo.CARRO, resultado.get(0).getTipo());
    }

    @Test
    void testCrearOActualizar_CopiaDatosPropietarioAlCrear() {
        when(vehiculoRepository.findByPlaca("ABC123")).thenReturn(Optional.empty());
        when(vehiculoRepository.save(any(Vehiculo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Vehiculo resultado = vehiculoService.crearOActualizar(vehiculoDTO);

        assertEquals("Juan Pérez", resultado.getPropietarioNombre());
        assertEquals("12345678", resultado.getPropietarioDocumento());
        assertEquals("3001234567", resultado.getPropietarioTelefono());
        assertEquals(TipoVehiculo.CARRO, resultado.getTipo());
    }

    @Test
    void testCrearOActualizar_ActualizaDatosPropietarioExistente() {
        Vehiculo existente = Vehiculo.builder().id(2L).placa("ABC123").marca("Anterior")
                .modelo("Anterior").color("Negro").tipo(TipoVehiculo.MOTO).build();
        when(vehiculoRepository.findByPlaca("ABC123")).thenReturn(Optional.of(existente));
        when(vehiculoRepository.save(any(Vehiculo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Vehiculo resultado = vehiculoService.crearOActualizar(vehiculoDTO);

        assertEquals(2L, resultado.getId());
        assertEquals("Toyota", resultado.getMarca());
        assertEquals("Corolla", resultado.getModelo());
        assertEquals("Rojo", resultado.getColor());
        assertEquals(TipoVehiculo.CARRO, resultado.getTipo());
        assertEquals("Juan Pérez", resultado.getPropietarioNombre());
    }

    @Test
    void testBuscar_CriterioNuloConTipoYPropietarioEnRespuesta() {
        when(vehiculoRepository.findAll()).thenReturn(Arrays.asList(vehiculo));

        List<VehiculoDTO> resultado = vehiculoService.buscar(null, TipoVehiculo.CARRO, null);

        assertEquals(1, resultado.size());
        assertEquals("Juan Pérez", resultado.get(0).getPropietarioNombre());
        assertEquals("12345678", resultado.get(0).getPropietarioDocumento());
        assertEquals("3001234567", resultado.get(0).getPropietarioTelefono());
    }

    @Test
    void testBuscar_ConCriterioYTipoFiltraCoincidencias() {
        Vehiculo moto = Vehiculo.builder().id(2L).placa("ABC124").marca("Honda")
                .modelo("CB").color("Azul").tipo(TipoVehiculo.MOTO).build();
        when(vehiculoRepository.findByPlacaContainingIgnoreCaseOrPropietarioNombreContainingIgnoreCaseOrPropietarioDocumentoContaining(
                "ABC", "ABC", "ABC")).thenReturn(Arrays.asList(vehiculo, moto));

        List<VehiculoDTO> resultado = vehiculoService.buscar("ABC", TipoVehiculo.CARRO, null);

        assertEquals(1, resultado.size());
        assertEquals("ABC123", resultado.get(0).getPlaca());
        verify(vehiculoRepository, never()).findAll();
    }

    @Test
    void testBuscar_CriterioEnBlancoUsaListadoCompleto() {
        when(vehiculoRepository.findAll()).thenReturn(Arrays.asList(vehiculo));

        List<VehiculoDTO> resultado = vehiculoService.buscar("   ", null, null);

        assertEquals(1, resultado.size());
        verify(vehiculoRepository).findAll();
        verify(vehiculoRepository, never()).findByPlacaContainingIgnoreCaseOrPropietarioNombreContainingIgnoreCaseOrPropietarioDocumentoContaining(
                anyString(), anyString(), anyString());
    }

    @Test
    void testBuscarPorPlaca_UsaPlacaSolicitada() {
        when(vehiculoRepository.findByPlaca("XYZ999")).thenReturn(Optional.empty());

        assertTrue(vehiculoService.buscarPorPlaca("XYZ999").isEmpty());

        verify(vehiculoRepository).findByPlaca("XYZ999");
    }
}
