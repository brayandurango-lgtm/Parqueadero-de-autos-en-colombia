package com.autoscolombia.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidad JPA que representa un registro de auditoría del sistema.
 */
@Entity
@Table(name = "auditoria_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditoriaLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", foreignKey = @ForeignKey(name = "fk_auditoria_usuario"))
    private Usuario usuario;

    @NotBlank(message = "La operación es obligatoria")
    @Size(max = 50, message = "La operación no puede exceder 50 caracteres")
    @Column(name = "operacion", nullable = false, length = 50)
    private String operacion;

    @Size(max = 50, message = "La entidad afectada no puede exceder 50 caracteres")
    @Column(name = "entidad_afectada", length = 50)
    private String entidadAfectada;

    @Column(name = "registro_id")
    private Long registroId;

    @Size(max = 45, message = "La IP de origen no puede exceder 45 caracteres")
    @Column(name = "ip_origen", length = 45)
    private String ipOrigen;

    @NotBlank(message = "El resultado es obligatorio")
    @Size(max = 20, message = "El resultado no puede exceder 20 caracteres")
    @Column(name = "resultado", nullable = false, length = 20)
    private String resultado;

    @NotNull(message = "La fecha y hora es obligatoria")
    @Column(name = "fecha_hora", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime fechaHora = LocalDateTime.now();

    @Column(name = "detalle", columnDefinition = "TEXT")
    private String detalle;

    @PrePersist
    public void prePersist() {
        if (this.fechaHora == null) {
            this.fechaHora = LocalDateTime.now();
        }
    }
}
