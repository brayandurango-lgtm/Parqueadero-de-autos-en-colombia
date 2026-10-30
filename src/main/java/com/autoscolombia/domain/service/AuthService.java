package com.autoscolombia.domain.service;

import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.infrastructure.exception.AuthenticationException;
import com.autoscolombia.infrastructure.exception.BusinessRuleException;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
import com.autoscolombia.presentation.dto.AuthResponse;
import com.autoscolombia.presentation.dto.LoginRequest;
import com.autoscolombia.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de autenticación y generación de tokens JWT.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // Contador de intentos fallidos por usuario (en memoria para simplificar)
    private final Map<String, Integer> intentosFallidos = new ConcurrentHashMap<>();
    private final Map<String, Long> bloqueosTemporales = new ConcurrentHashMap<>();

    private static final int MAX_INTENTOS = 3;
    private static final long BLOQUEO_MINUTOS = 15;

    /**
     * Realiza el login de un usuario y genera el token JWT.
     */
    @Transactional
    public AuthResponse login(LoginRequest loginRequest) {
        String correo = loginRequest.getCorreo();

        // Verificar si la cuenta está bloqueada temporalmente
        if (estaBloqueado(correo)) {
            throw new BusinessRuleException("Cuenta bloqueada temporalmente. Intente nuevamente en " + BLOQUEO_MINUTOS + " minutos.");
        }

        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new AuthenticationException("Credenciales inválidas"));

        if (!usuario.getActivo()) {
            throw new BusinessRuleException("La cuenta de usuario está inactiva");
        }

        if (!passwordEncoder.matches(loginRequest.getClave(), usuario.getClaveHash())) {
            registrarIntentoFallido(correo);
            throw new AuthenticationException("Credenciales inválidas");
        }

        // Login exitoso: limpiar contador de intentos
        intentosFallidos.remove(correo);
        bloqueosTemporales.remove(correo);

        // Generar token JWT
        UserDetails userDetails = User.builder()
                .username(usuario.getCorreo())
                .password(usuario.getClaveHash())
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + usuario.getTipo())))
                .build();

        String token = jwtUtil.generateToken(userDetails, usuario.getTipo().name());

        return AuthResponse.builder()
                .token(token)
                .tipoToken("Bearer")
                .usuarioId(usuario.getId())
                .nombre(usuario.getNombre())
                .apellido(usuario.getApellido())
                .correo(usuario.getCorreo())
                .tipo(usuario.getTipo())
                .build();
    }

    /**
     * Valida un token JWT y retorna los detalles del usuario.
     */
    public UserDetails validarToken(String token) {
        String username = jwtUtil.extractUsername(token);
        Usuario usuario = usuarioRepository.findByCorreo(username)
                .orElseThrow(() -> new AuthenticationException("Usuario no encontrado"));

        UserDetails userDetails = User.builder()
                .username(usuario.getCorreo())
                .password(usuario.getClaveHash())
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + usuario.getTipo())))
                .build();

        if (jwtUtil.validateToken(token, userDetails)) {
            return userDetails;
        }

        throw new AuthenticationException("Token inválido o expirado");
    }

    /**
     * Registra un intento fallido de login.
     */
    private void registrarIntentoFallido(String correo) {
        intentosFallidos.put(correo, intentosFallidos.getOrDefault(correo, 0) + 1);

        if (intentosFallidos.get(correo) >= MAX_INTENTOS) {
            bloqueosTemporales.put(correo, System.currentTimeMillis());
        }
    }

    /**
     * Verifica si una cuenta está bloqueada temporalmente.
     */
    private boolean estaBloqueado(String correo) {
        Long tiempoBloqueo = bloqueosTemporales.get(correo);
        if (tiempoBloqueo == null) {
            return false;
        }

        long tiempoTranscurrido = System.currentTimeMillis() - tiempoBloqueo;
        long bloqueoMs = BLOQUEO_MINUTOS * 60 * 1000;

        if (tiempoTranscurrido > bloqueoMs) {
            // El bloqueo ha expirado
            bloqueosTemporales.remove(correo);
            intentosFallidos.remove(correo);
            return false;
        }

        return true;
    }
}
