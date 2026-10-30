package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.domain.service.AuthService;
import com.autoscolombia.domain.service.UsuarioService;
import com.autoscolombia.presentation.dto.AuthResponse;
import com.autoscolombia.presentation.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {
    @Mock private AuthService authService;
    @Mock private UsuarioService usuarioService;
    @InjectMocks private AuthController controller;

    @Test
    void loginDelegaServicioYDevuelveOk() {
        LoginRequest request = LoginRequest.builder().correo("op@example.com").clave("secret").build();
        AuthResponse auth = AuthResponse.builder().token("jwt").correo("op@example.com").tipo(TipoUsuario.OPERADOR).build();
        when(authService.login(request)).thenReturn(auth);

        var response = controller.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(auth, response.getBody());
        verify(authService).login(request);
    }

    @Test
    void logoutDevuelve204() {
        var response = controller.logout();
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void meDevuelvePerfilSinHash() {
        Usuario usuario = Usuario.builder().id(3L).nombre("Ana").apellido("Pérez").documento("123")
                .correo("ana@example.com").telefono("3001").claveHash("do-not-return")
                .tipo(TipoUsuario.ADMINISTRADOR).activo(true).build();
        when(usuarioService.obtenerPorCorreo("ana@example.com")).thenReturn(Optional.of(usuario));
        var principal = User.withUsername("ana@example.com").password("hash").authorities("ROLE_ADMINISTRADOR").build();

        var response = controller.me(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Ana", response.getBody().getNombre());
        assertEquals("ana@example.com", response.getBody().getCorreo());
        assertEquals(TipoUsuario.ADMINISTRADOR, response.getBody().getTipo());
        verify(usuarioService).obtenerPorCorreo("ana@example.com");
    }

    @Test
    void meSinRegistroLanzaError() {
        when(usuarioService.obtenerPorCorreo("unknown@example.com")).thenReturn(Optional.empty());
        var principal = User.withUsername("unknown@example.com").password("hash").authorities("ROLE_OPERADOR").build();
        assertThrows(RuntimeException.class, () -> controller.me(principal));
    }
}
