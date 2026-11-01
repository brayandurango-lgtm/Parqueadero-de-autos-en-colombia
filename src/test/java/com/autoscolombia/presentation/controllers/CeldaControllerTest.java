package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.Celda;
import com.autoscolombia.domain.service.CeldaService;
import com.autoscolombia.presentation.dto.CambiarActivoCeldaDTO;
import com.autoscolombia.presentation.dto.CambiarEstadoCeldaDTO;
import com.autoscolombia.presentation.dto.CeldaActualizarDTO;
import com.autoscolombia.presentation.dto.CeldaCrearDTO;
import com.autoscolombia.presentation.dto.CeldaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El controlador de celdas solo proyecta y delega: los filtros del array heredado, el estado 201 del alta y
 * el reparto de permisos sobre las rutas administrativas. Las reglas de fondo se prueban en
 * {@code CeldaServiceTest} y los códigos HTTP reales en {@code CeldasIntegridadIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class CeldaControllerTest {

    @Mock private CeldaService celdaService;

    @InjectMocks private CeldaController controller;

    private final UserDetails actor = org.springframework.security.core.userdetails.User
            .withUsername("admin@autoscolombia.com")
            .password("{noop}sin-importancia")
            .authorities("ROLE_" + TipoUsuario.ADMINISTRADOR.name())
            .build();

    private Celda carroDisponible;
    private Celda motoOcupada;
    private CeldaDTO dtoCarro;
    private CeldaDTO dtoMoto;

    @BeforeEach
    void setUp() {
        carroDisponible = Celda.builder().id(1L).numero("A-01").piso(1)
                .tipoVehiculo(TipoVehiculo.CARRO).estado(EstadoCelda.DISPONIBLE).activo(true)
                .observaciones("Cerca de entrada").build();
        motoOcupada = Celda.builder().id(2L).numero("B-01").piso(2)
                .tipoVehiculo(TipoVehiculo.MOTO).estado(EstadoCelda.OCUPADA).activo(true).build();

        dtoCarro = CeldaDTO.builder().id(1L).numero("A-01").piso(1).tipoVehiculo(TipoVehiculo.CARRO)
                .estado(EstadoCelda.DISPONIBLE).activo(true).disponible(true)
                .observaciones("Cerca de entrada").build();
        dtoMoto = CeldaDTO.builder().id(2L).numero("B-01").piso(2).tipoVehiculo(TipoVehiculo.MOTO)
                .estado(EstadoCelda.OCUPADA).activo(true).disponible(false).build();
    }

    @Test
    void listarSinFiltrosProyectaTodasLasCeldasConElMapeoDelServicio() {
        when(celdaService.obtenerTodas()).thenReturn(List.of(carroDisponible, motoOcupada));
        when(celdaService.aDTO(carroDisponible)).thenReturn(dtoCarro);
        when(celdaService.aDTO(motoOcupada)).thenReturn(dtoMoto);

        ResponseEntity<List<CeldaDTO>> response = controller.listar(null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        assertEquals("Cerca de entrada", response.getBody().get(0).getObservaciones());
        assertEquals(TipoVehiculo.MOTO, response.getBody().get(1).getTipoVehiculo());
        assertEquals(Boolean.FALSE, response.getBody().get(1).getDisponible());
    }

    @Test
    void listarFiltraPorEstadoTipoYPiso() {
        when(celdaService.obtenerTodas()).thenReturn(List.of(carroDisponible, motoOcupada));
        when(celdaService.aDTO(carroDisponible)).thenReturn(dtoCarro);

        var response = controller.listar(EstadoCelda.DISPONIBLE, TipoVehiculo.CARRO, 1);

        assertEquals(1, response.getBody().size());
        assertEquals("A-01", response.getBody().get(0).getNumero());
        assertEquals(1, response.getBody().get(0).getPiso());
    }

    @Test
    void listarDevuelveListaVaciaSiNoCoincideElFiltro() {
        when(celdaService.obtenerTodas()).thenReturn(List.of(motoOcupada));

        assertTrue(controller.listar(EstadoCelda.DISPONIBLE, null, null).getBody().isEmpty());
    }

    @Test
    void listarDisponiblesDelegaEnLasCeldasAsignablesDelServicio() {
        when(celdaService.listarAsignables(TipoVehiculo.MOTO)).thenReturn(List.of(carroDisponible));
        when(celdaService.aDTO(carroDisponible)).thenReturn(dtoCarro);

        var response = controller.listarDisponibles(TipoVehiculo.MOTO);

        assertEquals(List.of("A-01"), response.getBody().stream().map(CeldaDTO::getNumero).toList());
        verify(celdaService).listarAsignables(TipoVehiculo.MOTO);
    }

    @Test
    void listarPaginaDelegaLosCincoFiltrosYLaPaginacion() {
        when(celdaService.listar(eq("A-0"), isNull(), eq(TipoVehiculo.CARRO), eq(2), eq(Boolean.TRUE), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(dtoCarro),
                        org.springframework.data.domain.PageRequest.of(0, 20), 1));

        var response = controller.listarPagina("A-0", null, TipoVehiculo.CARRO, 2, true, 0, 20);

        assertEquals(1, response.getBody().getTotalElements());
        assertEquals("A-01", response.getBody().getContent().get(0).getNumero());
    }

    @Test
    void obtenerPorIdMapeaRespuesta() {
        when(celdaService.obtenerPorId(1L)).thenReturn(carroDisponible);
        when(celdaService.aDTO(carroDisponible)).thenReturn(dtoCarro);

        var response = controller.obtenerPorId(1L);

        assertEquals("A-01", response.getBody().getNumero());
        assertEquals(EstadoCelda.DISPONIBLE, response.getBody().getEstado());
        assertEquals(Boolean.TRUE, response.getBody().getActivo());
    }

    @Test
    void crearResponde201ConLaCeldaYaProyectada() {
        CeldaCrearDTO dto = CeldaCrearDTO.builder().numero("A-09").piso(1)
                .tipoVehiculo(TipoVehiculo.CARRO).build();
        when(celdaService.crear(eq(dto), anyString())).thenReturn(carroDisponible);
        when(celdaService.aDTO(carroDisponible)).thenReturn(dtoCarro);

        var response = controller.crear(dto, actor);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("A-01", response.getBody().getNumero());
        verify(celdaService).crear(dto, "admin@autoscolombia.com");
    }

    @Test
    void actualizarDelegaLaEdicionYResponde200() {
        CeldaActualizarDTO dto = CeldaActualizarDTO.builder().numero("A-01").piso(3)
                .tipoVehiculo(TipoVehiculo.CARRO).observaciones("señal nueva").build();
        when(celdaService.actualizar(eq(1L), eq(dto), anyString())).thenReturn(carroDisponible);
        when(celdaService.aDTO(carroDisponible)).thenReturn(dtoCarro);

        var response = controller.actualizar(1L, dto, actor);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(celdaService).actualizar(1L, dto, "admin@autoscolombia.com");
    }

    @Test
    void cambiarEstadoInterpretaEstadoYDevuelveCelda() {
        when(celdaService.cambiarEstado(1L, EstadoCelda.MANTENIMIENTO, "admin@autoscolombia.com"))
                .thenReturn(carroDisponible);
        when(celdaService.aDTO(carroDisponible)).thenReturn(dtoCarro);

        var response = controller.cambiarEstado(1L,
                CambiarEstadoCeldaDTO.builder().estado(EstadoCelda.MANTENIMIENTO).build(), actor);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(EstadoCelda.DISPONIBLE, response.getBody().getEstado());
    }

    @Test
    void cambiarActivoDelegaElNuevoEstadoLogico() {
        when(celdaService.cambiarActivo(1L, false, "admin@autoscolombia.com")).thenReturn(carroDisponible);
        when(celdaService.aDTO(carroDisponible)).thenReturn(dtoCarro);

        var response = controller.cambiarActivo(1L, CambiarActivoCeldaDTO.builder().activo(false).build(), actor);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(celdaService).cambiarActivo(1L, false, "admin@autoscolombia.com");
    }
}
