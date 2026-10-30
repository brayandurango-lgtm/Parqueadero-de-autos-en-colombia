package com.autoscolombia.domain.model;

import com.autoscolombia.domain.enums.EstadoRegistro;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidad JPA que representa un registro de entrada/salida de vehículo.
 */
@Entity
@Table(name = "registro")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Registro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "El vehículo es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehiculo_id", nullable = false, foreignKey = @ForeignKey(name = "fk_registro_vehiculo"))
    private Vehiculo vehiculo;

    @NotNull(message = "La celda es obligatoria")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "celda_id", nullable = false, foreignKey = @ForeignKey(name = "fk_registro_celda"))
    private Celda celda;

    @NotNull(message = "El usuario de entrada es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_entrada_id", nullable = false, foreignKey = @ForeignKey(name = "fk_registro_usuario_entrada"))
    private Usuario usuarioEntrada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_salida_id", foreignKey = @ForeignKey(name = "fk_registro_usuario_salida"))
    private Usuario usuarioSalida;

    @NotNull(message = "La fecha y hora de entrada es obligatoria")
    @Column(name = "fecha_hora_entrada", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime fechaHoraEntrada = LocalDateTime.now();

    @Column(name = "fecha_hora_salida")
    private LocalDateTime fechaHoraSalida;

    @NotNull(message = "El estado es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    @Builder.Default
    private EstadoRegistro estado = EstadoRegistro.ACTIVO;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @PrePersist
    public void prePersist() {
        if (this.fechaHoraEntrada == null) {
            this.fechaHoraEntrada = LocalDateTime.now();
        }
        if (this.estado == null) {
            this.estado = EstadoRegistro.ACTIVO;
        }
    }
}
