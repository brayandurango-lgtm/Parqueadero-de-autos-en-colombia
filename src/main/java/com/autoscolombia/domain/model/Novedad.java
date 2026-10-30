package com.autoscolombia.domain.model;

import com.autoscolombia.domain.enums.TipoNovedad;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidad JPA que representa una novedad reportada en un registro.
 */
@Entity
@Table(name = "novedad")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Novedad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "El registro es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registro_id", nullable = false, foreignKey = @ForeignKey(name = "fk_novedad_registro"))
    private Registro registro;

    @NotNull(message = "El tipo de novedad es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoNovedad tipo;

    @NotBlank(message = "La descripción es obligatoria")
    @Column(name = "descripcion", nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @NotNull(message = "La fecha y hora es obligatoria")
    @Column(name = "fecha_hora", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime fechaHora = LocalDateTime.now();

    @NotNull(message = "El usuario que reporta es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reportado_por_id", nullable = false, foreignKey = @ForeignKey(name = "fk_novedad_usuario"))
    private Usuario reportadoPor;

    @Size(max = 255, message = "La URL de evidencia no puede exceder 255 caracteres")
    @Column(name = "evidencia_url", length = 255)
    private String evidenciaUrl;

    @PrePersist
    public void prePersist() {
        if (this.fechaHora == null) {
            this.fechaHora = LocalDateTime.now();
        }
    }
}
