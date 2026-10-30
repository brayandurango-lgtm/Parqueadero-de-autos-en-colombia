package com.autoscolombia.service;

import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.domain.service.AuthService;
import com.autoscolombia.infrastructure.exception.AuthenticationException;
import com.autoscolombia.infrastructure.exception.BusinessRuleException;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
import com.autoscolombia.presentation.dto.AuthResponse;
import com.autoscolombia.presentation.dto.LoginRequest;
import com.autoscolombia.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private LoginRequest loginRequest;
    private Usuario usuario;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        loginRequest = LoginRequest.builder()
                .correo("admin@autoscolombia.com")
                .clave("clave123")
                .build();

        usuario = Usuario.builder()
                .id(1L)
                .nombre("Carlos")
                .apellido("Ramírez")
                .correo("admin@autoscolombia.com")
                .claveHash("$2a$10$N9qo8uLOickgx2ZMRZoMy.Mrq4VfXFKDvPJhjKkJ0nU8RkO5.J9jq")
                .tipo(TipoUsuario.ADMINISTRADOR)
                .activo(true)
                .build();

        userDetails = User.builder()
                .username(usuario.getCorreo())
                .password(usuario.getClaveHash())
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                .build();
    }

    @Test
    void testLogin_Exito() {
        when(usuarioRepository.findByCorreo(anyString())).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtUtil.generateToken(any(UserDetails.class), anyString())).thenReturn("jwt-token");

        AuthResponse resultado = authService.login(loginRequest);

        assertNotNull(resultado);
        assertEquals("jwt-token", resultado.getToken());
        assertEquals("Carlos", resultado.getNombre());
        assertEquals(TipoUsuario.ADMINISTRADOR, resultado.getTipo());
    }

    @Test
    void testLogin_UsuarioNoEncontrado() {
        when(usuarioRepository.findByCorreo(anyString())).thenReturn(Optional.empty());

        assertThrows(AuthenticationException.class, () -> {
            authService.login(loginRequest);
        });
    }

    @Test
    void testLogin_ClaveIncorrecta() {
        when(usuarioRepository.findByCorreo(anyString())).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(AuthenticationException.class, () -> {
            authService.login(loginRequest);
        });
    }

    @Test
    void testLogin_UsuarioInactivo() {
        usuario.setActivo(false);
        when(usuarioRepository.findByCorreo(anyString())).thenReturn(Optional.of(usuario));
        lenient().when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> {
            authService.login(loginRequest);
        });
    }

    @Test
    void testValidarToken_Exito() {
        when(jwtUtil.extractUsername(anyString())).thenReturn(usuario.getCorreo());
        when(usuarioRepository.findByCorreo(anyString())).thenReturn(Optional.of(usuario));
        when(jwtUtil.validateToken(anyString(), any(UserDetails.class))).thenReturn(true);

        UserDetails resultado = authService.validarToken("valid-token");

        assertNotNull(resultado);
        assertEquals(usuario.getCorreo(), resultado.getUsername());
    }

    @Test
    void testValidarToken_UsuarioNoEncontrado() {
        when(jwtUtil.extractUsername(anyString())).thenReturn(usuario.getCorreo());
        when(usuarioRepository.findByCorreo(anyString())).thenReturn(Optional.empty());

        assertThrows(AuthenticationException.class, () -> {
            authService.validarToken("invalid-token");
        });
    }

    @Test
    void testValidarToken_TokenInvalido() {
        when(jwtUtil.extractUsername(anyString())).thenReturn(usuario.getCorreo());
        when(usuarioRepository.findByCorreo(anyString())).thenReturn(Optional.of(usuario));
        when(jwtUtil.validateToken(anyString(), any(UserDetails.class))).thenReturn(false);

        assertThrows(AuthenticationException.class, () -> {
            authService.validarToken("invalid-token");
        });
    }
}
