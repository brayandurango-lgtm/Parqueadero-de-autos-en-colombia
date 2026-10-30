package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.enums.TipoNovedad;
import com.autoscolombia.domain.service.NovedadService;
import com.autoscolombia.presentation.dto.NovedadDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NovedadControllerTest {
    @Mock private NovedadService novedadService;
    @InjectMocks private NovedadController controller;

    @Test
    void registrarDevuelve201YUsuarioAutenticado() {
        NovedadDTO dto = NovedadDTO.builder().registroId(1L).tipo(TipoNovedad.RAYON).descripcion("rayón").build();
        var principal = User.withUsername("op@example.com").password("hash").authorities("ROLE_OPERADOR").build();
        var response = controller.registrar(dto, principal);
        assertEquals(201, response.getStatusCode().value());
        assertSame(dto, response.getBody());
        verify(novedadService).registrar(dto, "op@example.com");
    }

    @Test
    void listarPorRegistroUsaFiltro() {
        when(novedadService.listarPorRegistro(4L)).thenReturn(List.of(NovedadDTO.builder().registroId(4L).build()));
        var result = controller.listar(4L, null, null, null);
        assertEquals(4L, result.getBody().get(0).getRegistroId());
        verify(novedadService).listarPorRegistro(4L);
    }

    @Test
    void listarSinRegistroDevuelvePendientes() {
        when(novedadService.listarPendientes()).thenReturn(List.of());
        assertTrue(controller.listar(null, "PENDIENTE", "2026-01-01", "2026-01-31").getBody().isEmpty());
        verify(novedadService).listarPendientes();
    }

    @Test
    void pendientesDelegaAlServicio() {
        when(novedadService.listarPendientes()).thenReturn(List.of(NovedadDTO.builder().registroId(2L).build()));
        assertEquals(2L, controller.listarPendientes().getBody().get(0).getRegistroId());
    }
}
