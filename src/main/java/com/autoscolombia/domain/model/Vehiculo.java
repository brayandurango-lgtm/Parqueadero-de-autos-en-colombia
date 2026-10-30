package com.autoscolombia.domain.model;

import com.autoscolombia.domain.enums.TipoVehiculo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidad JPA que representa un vehículo en el sistema.
 */
@Entity
@Table(name = "vehiculo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehiculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "La placa es obligatoria")
    @Pattern(regexp = "^[A-Z]{3}[0-9]{3}|[A-Z]{2}[0-9]{3}[A-Z]$", message = "La placa debe tener formato válido (ABC123 o AB123C)")
    @Size(max = 10, message = "La placa no puede exceder 10 caracteres")
    @Column(name = "placa", nullable = false, unique = true, length = 10)
    private String placa;

    @NotBlank(message = "La marca es obligatoria")
    @Size(max = 50, message = "La marca no puede exceder 50 caracteres")
    @Column(name = "marca", nullable = false, length = 50)
    private String marca;

    @NotBlank(message = "El modelo es obligatorio")
    @Size(max = 50, message = "El modelo no puede exceder 50 caracteres")
    @Column(name = "modelo", nullable = false, length = 50)
    private String modelo;

    @NotBlank(message = "El color es obligatorio")
    @Size(max = 30, message = "El color no puede exceder 30 caracteres")
    @Column(name = "color", nullable = false, length = 30)
    private String color;

    @NotNull(message = "El tipo de vehículo es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    @Builder.Default
    private TipoVehiculo tipo = TipoVehiculo.CARRO;

    @Size(max = 20, message = "El documento del propietario no puede exceder 20 caracteres")
    @Column(name = "propietario_documento", length = 20)
    private String propietarioDocumento;

    @Size(max = 120, message = "El nombre del propietario no puede exceder 120 caracteres")
    @Column(name = "propietario_nombre", length = 120)
    private String propietarioNombre;

    @Size(max = 20, message = "El teléfono del propietario no puede exceder 20 caracteres")
    @Column(name = "propietario_telefono", length = 20)
    private String propietarioTelefono;

    @NotNull(message = "La fecha de registro es obligatoria")
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.fechaRegistro == null) {
            this.fechaRegistro = LocalDateTime.now();
        }
        if (this.tipo == null) {
            this.tipo = TipoVehiculo.CARRO;
        }
    }
}
