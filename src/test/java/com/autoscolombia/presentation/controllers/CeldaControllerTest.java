package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.Celda;
import com.autoscolombia.domain.service.CeldaService;
import com.autoscolombia.presentation.dto.CeldaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CeldaControllerTest {
    @Mock private CeldaService celdaService;
    @InjectMocks private CeldaController controller;

    private Celda carroDisponible;
    private Celda motoOcupada;

    @BeforeEach
    void setUp() {
        carroDisponible = Celda.builder().id(1L).numero("A-01").piso(1)
                .tipoVehiculo(TipoVehiculo.CARRO).estado(EstadoCelda.DISPONIBLE).observaciones("Cerca de entrada").build();
        motoOcupada = Celda.builder().id(2L).numero("B-01").piso(2)
                .tipoVehiculo(TipoVehiculo.MOTO).estado(EstadoCelda.OCUPADA).build();
    }

    @Test
    void listarSinFiltrosMapeaTodasLasCeldas() {
        when(celdaService.obtenerTodas()).thenReturn(List.of(carroDisponible, motoOcupada));
        ResponseEntity<List<CeldaDTO>> response = controller.listar(null, null, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        assertEquals("Cerca de entrada", response.getBody().get(0).getObservaciones());
        assertEquals(TipoVehiculo.MOTO, response.getBody().get(1).getTipoVehiculo());
    }

    @Test
    void listarFiltraPorEstadoTipoYPiso() {
        when(celdaService.obtenerTodas()).thenReturn(List.of(carroDisponible, motoOcupada));
        var response = controller.listar(EstadoCelda.DISPONIBLE, TipoVehiculo.CARRO, 1);
        assertEquals(1, response.getBody().size());
        assertEquals("A-01", response.getBody().get(0).getNumero());
        assertEquals(1, response.getBody().get(0).getPiso());
    }

    @Test
    void listarDevuelveListaVaciaSiNoCoincideFiltro() {
        when(celdaService.obtenerTodas()).thenReturn(List.of(motoOcupada));
        assertTrue(controller.listar(EstadoCelda.DISPONIBLE, null, null).getBody().isEmpty());
    }

    @Test
    void listarDisponiblesSinTipoIncluyeTodasLasLibres() {
        Celda motoLibre = Celda.builder().id(3L).numero("B-02").piso(2)
                .tipoVehiculo(TipoVehiculo.MOTO).estado(EstadoCelda.DISPONIBLE).build();
        when(celdaService.obtenerTodas()).thenReturn(List.of(carroDisponible, motoOcupada, motoLibre));
        var response = controller.listarDisponibles(null);
        assertEquals(List.of("A-01", "B-02"), response.getBody().stream().map(CeldaDTO::getNumero).toList());
    }

    @Test
    void listarDisponiblesFiltraPorTipo() {
        Celda motoLibre = Celda.builder().id(3L).numero("B-02").piso(2)
                .tipoVehiculo(TipoVehiculo.MOTO).estado(EstadoCelda.DISPONIBLE).build();
        when(celdaService.obtenerTodas()).thenReturn(List.of(carroDisponible, motoLibre));
        var response = controller.listarDisponibles(TipoVehiculo.MOTO);
        assertEquals(1, response.getBody().size());
        assertEquals("B-02", response.getBody().get(0).getNumero());
    }

    @Test
    void obtenerPorIdMapeaRespuesta() {
        when(celdaService.obtenerPorId(1L)).thenReturn(carroDisponible);
        var response = controller.obtenerPorId(1L);
        assertEquals("A-01", response.getBody().getNumero());
        assertEquals(EstadoCelda.DISPONIBLE, response.getBody().getEstado());
    }

    @Test
    void cambiarEstadoInterpretaEstadoYDevuelveCelda() {
        carroDisponible.setEstado(EstadoCelda.MANTENIMIENTO);
        when(celdaService.cambiarEstado(1L, EstadoCelda.MANTENIMIENTO)).thenReturn(carroDisponible);
        var response = controller.cambiarEstado(1L, Map.of("estado", "MANTENIMIENTO"));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(EstadoCelda.MANTENIMIENTO, response.getBody().getEstado());
    }
}
