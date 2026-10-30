package com.autoscolombia.domain.model;

import com.autoscolombia.domain.enums.TipoUsuario;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidad JPA que representa un usuario del sistema.
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 80, message = "El nombre no puede exceder 80 caracteres")
    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 80, message = "El apellido no puede exceder 80 caracteres")
    @Column(name = "apellido", nullable = false, length = 80)
    private String apellido;

    @NotBlank(message = "El documento es obligatorio")
    @Size(max = 20, message = "El documento no puede exceder 20 caracteres")
    @Column(name = "documento", nullable = false, unique = true, length = 20)
    private String documento;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo debe tener un formato válido")
    @Size(max = 120, message = "El correo no puede exceder 120 caracteres")
    @Column(name = "correo", nullable = false, unique = true, length = 120)
    private String correo;

    @Size(max = 20, message = "El teléfono no puede exceder 20 caracteres")
    @Column(name = "telefono", length = 20)
    private String telefono;

    @NotBlank(message = "La clave hash es obligatoria")
    @Column(name = "clave_hash", nullable = false)
    private String claveHash;

    @NotNull(message = "El tipo de usuario es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    @Builder.Default
    private TipoUsuario tipo = TipoUsuario.OPERADOR;

    @NotNull(message = "El estado activo es obligatorio")
    @Column(name = "activo", nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @NotNull(message = "La fecha de registro es obligatoria")
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.fechaRegistro == null) {
            this.fechaRegistro = LocalDateTime.now();
        }
        if (this.activo == null) {
            this.activo = true;
        }
        if (this.tipo == null) {
            this.tipo = TipoUsuario.OPERADOR;
        }
    }
}
