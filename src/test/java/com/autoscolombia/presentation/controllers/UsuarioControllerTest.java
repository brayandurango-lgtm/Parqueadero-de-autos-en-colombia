package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.domain.service.UsuarioService;
import com.autoscolombia.presentation.dto.UsuarioDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioControllerTest {
    @Mock private UsuarioService usuarioService;
    @InjectMocks private UsuarioController controller;

    @Test
    void listarDevuelvePerfilesDeUsuario() {
        List<UsuarioDTO> users = List.of(UsuarioDTO.builder().id(1L).correo("admin@example.com").build());
        when(usuarioService.obtenerTodos()).thenReturn(users);
        assertSame(users, controller.listar().getBody());
    }

    @Test
    void crearAsignaIdYDevuelve201() {
        UsuarioDTO dto = UsuarioDTO.builder().nombre("Ana").correo("ana@example.com").build();
        Usuario user = Usuario.builder().id(3L).build();
        when(usuarioService.crear(dto, "clave123")).thenReturn(user);
        var response = controller.crear(dto);
        assertEquals(201, response.getStatusCode().value());
        assertEquals(3L, dto.getId());
        assertSame(dto, response.getBody());
    }

    @Test
    void actualizarDevuelveDto() {
        UsuarioDTO dto = UsuarioDTO.builder().nombre("Updated").build();
        var response = controller.actualizar(1L, dto);
        assertEquals(200, response.getStatusCode().value());
        assertSame(dto, response.getBody());
        verify(usuarioService).actualizar(1L, dto);
    }

    @Test
    void eliminarDevuelve204() {
        var response = controller.eliminar(1L);
        assertEquals(204, response.getStatusCode().value());
        verify(usuarioService).eliminar(1L);
    }
}
