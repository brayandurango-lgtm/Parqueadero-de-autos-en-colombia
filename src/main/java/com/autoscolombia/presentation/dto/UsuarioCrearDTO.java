package com.autoscolombia.presentation.dto;

import com.autoscolombia.domain.enums.TipoUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Solicitud de creación de una cuenta del personal. Es el único DTO del sistema que transporta la
 * contraseña: la edición ({@link UsuarioDTO}) y todas las respuestas siguen sin llevarla.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioCrearDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 80, message = "El nombre no puede exceder 80 caracteres")
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 80, message = "El apellido no puede exceder 80 caracteres")
    private String apellido;

    @NotBlank(message = "El documento es obligatorio")
    @Size(max = 20, message = "El documento no puede exceder 20 caracteres")
    private String documento;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo debe tener un formato válido")
    @Size(max = 120, message = "El correo no puede exceder 120 caracteres")
    private String correo;

    @Size(max = 20, message = "El teléfono no puede exceder 20 caracteres")
    private String telefono;

    @NotNull(message = "El tipo de usuario es obligatorio")
    private TipoUsuario tipo;

    private Boolean activo;

    /**
     * Contraseña inicial. No se recorta, no se normaliza y no se escribe en {@code toString}:
     * la política de longitud la validan las anotaciones y el límite real del algoritmo se comprueba
     * en {@link ContrasenaDentroDelLimiteBCrypt}.
     */
    @ToString.Exclude
    @NotBlank(message = "La clave es obligatoria")
    @Size(min = 15, message = "La clave debe tener al menos 15 caracteres")
    @ContrasenaDentroDelLimiteBCrypt
    private String clave;
}
