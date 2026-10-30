package com.autoscolombia.presentation.dto;

import com.autoscolombia.domain.enums.TipoUsuario;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para respuesta de autenticación.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String tipoToken;
    private Long usuarioId;
    private String nombre;
    private String apellido;
    private String correo;
    private TipoUsuario tipo;
}
