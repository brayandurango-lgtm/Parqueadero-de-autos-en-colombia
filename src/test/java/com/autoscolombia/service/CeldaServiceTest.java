package com.autoscolombia.service;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.Celda;
import com.autoscolombia.domain.service.CeldaService;
import com.autoscolombia.infrastructure.exception.BusinessRuleException;
import com.autoscolombia.infrastructure.exception.ResourceNotFoundException;
import com.autoscolombia.infrastructure.repository.CeldaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CeldaServiceTest {

    @Mock
    private CeldaRepository celdaRepository;

    @InjectMocks
    private CeldaService celdaService;

    private Celda celda;

    @BeforeEach
    void setUp() {
        celda = Celda.builder()
                .id(1L)
                .numero("A-01")
                .piso(1)
                .tipoVehiculo(TipoVehiculo.CARRO)
                .estado(EstadoCelda.DISPONIBLE)
                .build();
    }

    @Test
    void testAsignarCeldaDisponible_Exito() {
        when(celdaRepository.findFirstByTipoVehiculoAndEstadoOrderByNumeroAsc(
                eq(TipoVehiculo.CARRO), eq(EstadoCelda.DISPONIBLE)))
                .thenReturn(Optional.of(celda));
        when(celdaRepository.save(any(Celda.class))).thenReturn(celda);

        Celda resultado = celdaService.asignarCeldaDisponible(TipoVehiculo.CARRO);

        assertNotNull(resultado);
        assertEquals(EstadoCelda.OCUPADA, resultado.getEstado());
        verify(celdaRepository, times(1)).save(any(Celda.class));
    }

    @Test
    void testAsignarCeldaDisponible_SinCeldasDisponibles() {
        when(celdaRepository.findFirstByTipoVehiculoAndEstadoOrderByNumeroAsc(
                eq(TipoVehiculo.CARRO), eq(EstadoCelda.DISPONIBLE)))
                .thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, () -> {
            celdaService.asignarCeldaDisponible(TipoVehiculo.CARRO);
        });
    }

    @Test
    void testLiberarCelda_Exito() {
        celda.setEstado(EstadoCelda.OCUPADA);
        when(celdaRepository.findById(1L)).thenReturn(Optional.of(celda));
        when(celdaRepository.save(any(Celda.class))).thenReturn(celda);

        celdaService.liberarCelda(1L);

        assertEquals(EstadoCelda.DISPONIBLE, celda.getEstado());
        verify(celdaRepository, times(1)).save(any(Celda.class));
    }

    @Test
    void testLiberarCelda_CeldaNoEncontrada() {
        when(celdaRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            celdaService.liberarCelda(1L);
        });
    }

    @Test
    void testContarCeldasPorEstado() {
        when(celdaRepository.countByEstado(EstadoCelda.DISPONIBLE)).thenReturn(10L);
        when(celdaRepository.countByEstado(EstadoCelda.OCUPADA)).thenReturn(5L);
        when(celdaRepository.countByEstado(EstadoCelda.MANTENIMIENTO)).thenReturn(2L);

        Map<EstadoCelda, Long> resultado = celdaService.contarCeldasPorEstado();

        assertEquals(10L, resultado.get(EstadoCelda.DISPONIBLE));
        assertEquals(5L, resultado.get(EstadoCelda.OCUPADA));
        assertEquals(2L, resultado.get(EstadoCelda.MANTENIMIENTO));
    }

    @Test
    void testObtenerTodas() {
        when(celdaRepository.findAll()).thenReturn(Arrays.asList(celda));

        var resultado = celdaService.obtenerTodas();

        assertFalse(resultado.isEmpty());
        assertEquals(1, resultado.size());
    }

    @Test
    void testObtenerPorId_Encontrado() {
        when(celdaRepository.findById(1L)).thenReturn(Optional.of(celda));

        Celda resultado = celdaService.obtenerPorId(1L);

        assertNotNull(resultado);
        assertEquals("A-01", resultado.getNumero());
    }

    @Test
    void testObtenerPorId_NoEncontrado() {
        when(celdaRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            celdaService.obtenerPorId(1L);
        });
    }

    @Test
    void testCambiarEstado() {
        when(celdaRepository.findById(1L)).thenReturn(Optional.of(celda));
        when(celdaRepository.save(any(Celda.class))).thenReturn(celda);

        Celda resultado = celdaService.cambiarEstado(1L, EstadoCelda.MANTENIMIENTO);

        assertEquals(EstadoCelda.MANTENIMIENTO, resultado.getEstado());
        verify(celdaRepository, times(1)).save(any(Celda.class));
    }
}
