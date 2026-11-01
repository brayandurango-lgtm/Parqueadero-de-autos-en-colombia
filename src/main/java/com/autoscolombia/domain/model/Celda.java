package com.autoscolombia.domain.model;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.TipoVehiculo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Entidad JPA que representa una celda de parqueadero.
 */
@Entity
@Table(name = "celda",
        uniqueConstraints = @UniqueConstraint(name = "uk_celda_numero", columnNames = "numero"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Celda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El número de celda es obligatorio")
    @Size(max = 10, message = "El número de celda no puede exceder 10 caracteres")
    @Column(name = "numero", nullable = false, length = 10)
    private String numero;

    @NotNull(message = "El piso es obligatorio")
    @Column(name = "piso", nullable = false)
    private Integer piso;

    @NotNull(message = "El tipo de vehículo es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_vehiculo", nullable = false)
    @Builder.Default
    private TipoVehiculo tipoVehiculo = TipoVehiculo.CARRO;

    @NotNull(message = "El estado es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    @Builder.Default
    private EstadoCelda estado = EstadoCelda.DISPONIBLE;

    @Size(max = 255, message = "Las observaciones no pueden exceder 255 caracteres")
    @Column(name = "observaciones", length = 255)
    private String observaciones;

    /**
     * Baja lógica. No es un cuarto estado operativo: una celda inactiva conserva el {@code estado} que tenía
     * y simplemente deja de ser asignable.
     */
    @Column(name = "activo", nullable = false)
    @Builder.Default
    private Boolean activo = Boolean.TRUE;

    @PrePersist
    public void prePersist() {
        if (this.tipoVehiculo == null) {
            this.tipoVehiculo = TipoVehiculo.CARRO;
        }
        if (this.estado == null) {
            this.estado = EstadoCelda.DISPONIBLE;
        }
        if (this.activo == null) {
            this.activo = Boolean.TRUE;
        }
    }
}
