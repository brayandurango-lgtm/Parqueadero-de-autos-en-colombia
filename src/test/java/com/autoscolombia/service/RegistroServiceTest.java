package com.autoscolombia.service;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.EstadoRegistro;
import com.autoscolombia.domain.enums.TipoComprobante;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.*;
import com.autoscolombia.domain.service.AuditoriaService;
import com.autoscolombia.domain.service.CeldaService;
import com.autoscolombia.domain.service.PicoYPlacaService;
import com.autoscolombia.domain.service.RegistroService;
import com.autoscolombia.domain.service.VehiculoService;
import com.autoscolombia.infrastructure.exception.BusinessRuleException;
import com.autoscolombia.infrastructure.exception.ResourceNotFoundException;
import com.autoscolombia.infrastructure.repository.*;
import com.autoscolombia.presentation.dto.RegistroEntradaDTO;
import com.autoscolombia.presentation.dto.RegistroSalidaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistroServiceTest {

    @Mock
    private RegistroRepository registroRepository;

    @Mock
    private VehiculoRepository vehiculoRepository;

    @Mock
    private CeldaRepository celdaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ComprobanteRepository comprobanteRepository;

    @Mock
    private VehiculoService vehiculoService;

    @Mock
    private CeldaService celdaService;

    @Mock
    private PicoYPlacaService picoYPlacaService;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private RegistroService registroService;

    private RegistroEntradaDTO entradaDTO;
    private RegistroSalidaDTO salidaDTO;
    private Vehiculo vehiculo;
    private Celda celda;
    private Usuario usuario;
    private Registro registro;

    @BeforeEach
    void setUp() {
        entradaDTO = RegistroEntradaDTO.builder()
                .placa("ABC123")
                .marca("Toyota")
                .modelo("Corolla")
                .color("Rojo")
                .tipo(TipoVehiculo.CARRO)
                .propietarioDocumento("12345678")
                .propietarioNombre("Juan Pérez")
                .propietarioTelefono("3001234567")
                .build();

        salidaDTO = RegistroSalidaDTO.builder()
                .observaciones("Sin novedades")
                .build();

        vehiculo = Vehiculo.builder()
                .id(1L)
                .placa("ABC123")
                .marca("Toyota")
                .modelo("Corolla")
                .color("Rojo")
                .tipo(TipoVehiculo.CARRO)
                .build();

        celda = Celda.builder()
                .id(1L)
                .numero("A-01")
                .piso(1)
                .tipoVehiculo(TipoVehiculo.CARRO)
                .estado(EstadoCelda.DISPONIBLE)
                .build();

        usuario = Usuario.builder()
                .id(1L)
                .nombre("María")
                .apellido("López")
                .correo("operador@autoscolombia.com")
                .build();

        registro = Registro.builder()
                .id(1L)
                .vehiculo(vehiculo)
                .celda(celda)
                .usuarioEntrada(usuario)
                .fechaHoraEntrada(LocalDateTime.now())
                .estado(EstadoRegistro.ACTIVO)
                .build();
    }

    @Test
    void testRegistrarEntrada_Exito() {
        when(picoYPlacaService.puedeTransitar(anyString())).thenReturn(true);
        when(registroRepository.findByVehiculoPlacaAndEstado(anyString(), eq(EstadoRegistro.ACTIVO)))
                .thenReturn(Optional.empty());
        when(vehiculoService.crearOActualizar(any())).thenReturn(vehiculo);
        when(celdaService.asignarCeldaDisponible(any(TipoVehiculo.class))).thenReturn(celda);
        when(usuarioRepository.findByCorreo(anyString())).thenReturn(Optional.of(usuario));
        when(registroRepository.save(any(Registro.class))).thenReturn(registro);

        Registro resultado = registroService.registrarEntrada(entradaDTO, "operador@autoscolombia.com");

        assertNotNull(resultado);
        assertEquals(EstadoRegistro.ACTIVO, resultado.getEstado());
        verify(registroRepository, times(1)).save(any(Registro.class));
    }

    @Test
    void testRegistrarEntrada_ConClienteValido_VinculaCliente() {
        entradaDTO.setClienteId(10L);
        when(picoYPlacaService.puedeTransitar(anyString())).thenReturn(true);
        when(registroRepository.findByVehiculoPlacaAndEstado(anyString(), eq(EstadoRegistro.ACTIVO)))
                .thenReturn(Optional.empty());
        when(vehiculoService.crearOActualizar(any())).thenReturn(vehiculo);
        when(vehiculoService.vincularCliente(eq("ABC123"), eq(10L), eq("operador@autoscolombia.com")))
                .thenReturn(vehiculo);
        when(celdaService.asignarCeldaDisponible(any(TipoVehiculo.class))).thenReturn(celda);
        when(usuarioRepository.findByCorreo(anyString())).thenReturn(Optional.of(usuario));
        when(registroRepository.save(any(Registro.class))).thenReturn(registro);

        Registro resultado = registroService.registrarEntrada(entradaDTO, "operador@autoscolombia.com");

        assertNotNull(resultado);
        verify(vehiculoService).vincularCliente("ABC123", 10L, "operador@autoscolombia.com");
    }

    @Test
    void testRegistrarEntrada_VehiculoExistenteConClienteInactivo_RechazaIngreso() {
        Cliente clienteInactivo = Cliente.builder().id(5L).activo(false).build();
        Vehiculo vehiculoConClienteInactivo = Vehiculo.builder().id(1L).placa("ABC123").cliente(clienteInactivo).build();
        when(picoYPlacaService.puedeTransitar(anyString())).thenReturn(true);
        when(vehiculoRepository.buscarConBloqueoPorPlaca("ABC123")).thenReturn(Optional.of(vehiculoConClienteInactivo));
        when(registroRepository.findByVehiculoPlacaAndEstado("ABC123", EstadoRegistro.ACTIVO)).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, () ->
                registroService.registrarEntrada(entradaDTO, "operador@autoscolombia.com"));
    }

    @Test
    void testRegistrarEntrada_PicoYPlacaRestringe() {
        when(picoYPlacaService.puedeTransitar(anyString())).thenReturn(false);
        when(picoYPlacaService.obtenerMensajeRestriccion(anyString()))
                .thenReturn("Vehículo restringido por Pico y Placa");

        assertThrows(BusinessRuleException.class, () -> {
            registroService.registrarEntrada(entradaDTO, "operador@autoscolombia.com");
        });
    }

    @Test
    void testRegistrarEntrada_RegistroActivoExistente() {
        when(picoYPlacaService.puedeTransitar(anyString())).thenReturn(true);
        when(registroRepository.findByVehiculoPlacaAndEstado(anyString(), eq(EstadoRegistro.ACTIVO)))
                .thenReturn(Optional.of(registro));

        assertThrows(BusinessRuleException.class, () -> {
            registroService.registrarEntrada(entradaDTO, "operador@autoscolombia.com");
        });
    }

    @Test
    void testRegistrarSalida_Exito() {
        when(registroRepository.findByVehiculoPlacaAndEstado(anyString(), eq(EstadoRegistro.ACTIVO)))
                .thenReturn(Optional.of(registro));
        when(usuarioRepository.findByCorreo(anyString())).thenReturn(Optional.of(usuario));
        when(registroRepository.save(any(Registro.class))).thenReturn(registro);

        Registro resultado = registroService.registrarSalida("ABC123", salidaDTO, "operador@autoscolombia.com");

        assertNotNull(resultado);
        assertEquals(EstadoRegistro.CERRADO, resultado.getEstado());
        assertNotNull(resultado.getFechaHoraSalida());
        // B3: la salida libera la celda de SU registro, con el identificador del registro para que el
        // servicio pueda rechazar una celda ocupada por otro. Aquí ambos fixtures valen 1L.
        verify(celdaService, times(1)).liberarCelda(1L, 1L);
    }

    @Test
    void testRegistrarSalida_RegistroNoEncontrado() {
        when(registroRepository.findByVehiculoPlacaAndEstado(anyString(), eq(EstadoRegistro.ACTIVO)))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            registroService.registrarSalida("ABC123", salidaDTO, "operador@autoscolombia.com");
        });
    }

    @Test
    void testCancelarRegistro_Exito() {
        when(registroRepository.findById(1L)).thenReturn(Optional.of(registro));
        when(usuarioRepository.findByCorreo(anyString())).thenReturn(Optional.of(usuario));
        when(registroRepository.save(any(Registro.class))).thenReturn(registro);

        Registro resultado = registroService.cancelarRegistro(1L, "Cancelación por error", "operador@autoscolombia.com");

        assertNotNull(resultado);
        assertEquals(EstadoRegistro.CANCELADO, resultado.getEstado());
        verify(celdaService, times(1)).liberarCelda(1L, 1L);
    }

    @Test
    void testCancelarRegistro_RegistroNoActivo() {
        registro.setEstado(EstadoRegistro.CERRADO);
        when(registroRepository.findById(1L)).thenReturn(Optional.of(registro));

        assertThrows(BusinessRuleException.class, () -> {
            registroService.cancelarRegistro(1L, "Cancelación", "operador@autoscolombia.com");
        });
    }

    @Test
    void testCalcularDuracion_ConSalida() {
        registro.setFechaHoraEntrada(LocalDateTime.of(2026, 1, 1, 10, 0));
        registro.setFechaHoraSalida(LocalDateTime.of(2026, 1, 1, 12, 30));

        var duracion = registroService.calcularDuracion(registro);

        assertEquals(150, duracion.toMinutes());
    }

    @Test
    void testConsultarActivoPorPlaca_Encontrado() {
        when(registroRepository.findByVehiculoPlacaAndEstado(anyString(), eq(EstadoRegistro.ACTIVO)))
                .thenReturn(Optional.of(registro));

        Optional<Registro> resultado = registroService.consultarActivoPorPlaca("ABC123");

        assertTrue(resultado.isPresent());
    }

    @Test
    void testConsultarActivoPorPlaca_NoEncontrado() {
        when(registroRepository.findByVehiculoPlacaAndEstado(anyString(), eq(EstadoRegistro.ACTIVO)))
                .thenReturn(Optional.empty());

        Optional<Registro> resultado = registroService.consultarActivoPorPlaca("ABC123");

        assertFalse(resultado.isPresent());
    }
}
