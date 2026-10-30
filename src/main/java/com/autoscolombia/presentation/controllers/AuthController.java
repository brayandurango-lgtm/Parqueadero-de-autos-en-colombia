package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.domain.service.AuthService;
import com.autoscolombia.domain.service.UsuarioService;
import com.autoscolombia.presentation.dto.AuthResponse;
import com.autoscolombia.presentation.dto.LoginRequest;
import com.autoscolombia.presentation.dto.UsuarioDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para autenticación y autorización.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Operaciones de login y gestión de tokens")
public class AuthController {

    private final AuthService authService;
    private final UsuarioService usuarioService;

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Autentica un usuario y retorna un token JWT")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        AuthResponse response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión", description = "Cierra la sesión del usuario actual")
    public ResponseEntity<Void> logout() {
        // En una implementación con JWT stateless, el logout se maneja en el cliente
        // eliminando el token. Aquí simplemente retornamos 204.
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Obtener usuario actual", description = "Retorna la información del usuario autenticado")
    public ResponseEntity<UsuarioDTO> me(@AuthenticationPrincipal UserDetails userDetails) {
        Usuario usuario = usuarioService.obtenerPorCorreo(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        UsuarioDTO dto = UsuarioDTO.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .apellido(usuario.getApellido())
                .documento(usuario.getDocumento())
                .correo(usuario.getCorreo())
                .telefono(usuario.getTelefono())
                .tipo(usuario.getTipo())
                .activo(usuario.getActivo())
                .build();
        return ResponseEntity.ok(dto);
    }
}
