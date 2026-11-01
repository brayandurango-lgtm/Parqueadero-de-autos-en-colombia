package com.autoscolombia.domain.model;

import com.autoscolombia.domain.enums.TipoDocumento;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidad JPA que representa al titular o usuaria del servicio de parqueadero.
 *
 * <p>No es una cuenta de acceso: no tiene contraseña ni rol, y por eso no puede autenticarse.
 * {@link Usuario} sigue representando únicamente al personal.
 */
@Entity
@Table(name = "cliente",
        uniqueConstraints = @UniqueConstraint(name = "uk_cliente_tipo_documento",
                columnNames = {"tipo_documento", "documento"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "El tipo de documento es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 20)
    private TipoDocumento tipoDocumento;

    @NotNull(message = "El documento es obligatorio")
    @Size(max = 20, message = "El documento no puede exceder 20 caracteres")
    @Column(name = "documento", nullable = false, length = 20)
    private String documento;

    @NotNull(message = "El nombre completo es obligatorio")
    @Size(max = 120, message = "El nombre completo no puede exceder 120 caracteres")
    @Column(name = "nombre_completo", nullable = false, length = 120)
    private String nombreCompleto;

    @NotNull(message = "El teléfono es obligatorio")
    @Size(max = 20, message = "El teléfono no puede exceder 20 caracteres")
    @Column(name = "telefono", nullable = false, length = 20)
    private String telefono;

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
    }
}
