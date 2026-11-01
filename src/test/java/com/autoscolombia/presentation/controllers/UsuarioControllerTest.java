package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.domain.service.UsuarioService;
import com.autoscolombia.presentation.dto.CambiarEstadoUsuarioDTO;
import com.autoscolombia.presentation.dto.UsuarioCrearDTO;
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
    void crearRespondeElPerfilPersistidoSinDevolverLaClave() {
        UsuarioCrearDTO solicitud = UsuarioCrearDTO.builder().nombre("Ana").correo("ana@example.com")
                .clave("Clave inicial de prueba 15").build();
        Usuario persistido = Usuario.builder().id(3L).nombre("Ana").correo("ana@example.com")
                .documento("12345678").claveHash("hash-bcrypt").tipo(TipoUsuario.OPERADOR).activo(true).build();
        UsuarioDTO proyeccion = UsuarioDTO.builder().id(3L).nombre("Ana").correo("ana@example.com")
                .documento("12345678").tipo(TipoUsuario.OPERADOR).activo(true).build();
        when(usuarioService.crear(solicitud)).thenReturn(persistido);
        when(usuarioService.aDTO(persistido)).thenReturn(proyeccion);

        var response = controller.crear(solicitud);

        assertEquals(201, response.getStatusCode().value());
        assertSame(proyeccion, response.getBody());
        assertEquals(3L, response.getBody().getId());
        assertTrue(response.getBody().getActivo(), "La respuesta refleja el estado persistido");
    }

    @Test
    void actualizarRespondeLaProyeccionDelServicio() {
        UsuarioDTO dto = UsuarioDTO.builder().nombre("Updated").build();
        Usuario persistido = Usuario.builder().id(1L).nombre("Updated").claveHash("hash-no-visible").build();
        when(usuarioService.actualizar(1L, dto)).thenReturn(persistido);
        when(usuarioService.aDTO(persistido)).thenReturn(UsuarioDTO.builder().id(1L).nombre("Updated").build());

        var response = controller.actualizar(1L, dto);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Updated", response.getBody().getNombre());
        verify(usuarioService).actualizar(1L, dto);
    }

    @Test
    void cambiarEstadoRespondeElNuevoEstado() {
        Usuario persistido = Usuario.builder().id(7L).activo(false).build();
        when(usuarioService.cambiarEstado(7L, false)).thenReturn(persistido);
        when(usuarioService.aDTO(persistido)).thenReturn(UsuarioDTO.builder().id(7L).activo(false).build());

        var response = controller.cambiarEstado(7L, CambiarEstadoUsuarioDTO.builder().activo(false).build());

        assertEquals(200, response.getStatusCode().value());
        assertFalse(response.getBody().getActivo());
        verify(usuarioService).cambiarEstado(7L, false);
    }

    @Test
    void eliminarDevuelve204() {
        var response = controller.eliminar(1L);
        assertEquals(204, response.getStatusCode().value());
        verify(usuarioService).eliminar(1L);
    }
}
