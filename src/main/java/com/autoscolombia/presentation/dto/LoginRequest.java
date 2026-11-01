package com.autoscolombia.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * DTO para solicitud de login.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo debe tener un formato válido")
    private String correo;

    /**
     * Contraseña en texto plano de la solicitud. Se excluye del {@code toString} generado por Lombok para
     * que un registro accidental del DTO (o de la excepción de validación que lo incluye) no decrete la
     * credencial en el log.
     */
    @ToString.Exclude
    @NotBlank(message = "La clave es obligatoria")
    private String clave;

    @Override
    public String toString() {
        return "LoginRequest(correo=" + correo + ", clave=***)";
    }
}
