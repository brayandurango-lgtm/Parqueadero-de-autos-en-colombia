package com.autoscolombia.domain.model;

import com.autoscolombia.domain.enums.TipoComprobante;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidad JPA que representa un comprobante de entrada o salida.
 */
@Entity
@Table(name = "comprobante")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comprobante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "El registro es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registro_id", nullable = false, foreignKey = @ForeignKey(name = "fk_comprobante_registro"))
    private Registro registro;

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 40, message = "El código no puede exceder 40 caracteres")
    @Column(name = "codigo", nullable = false, unique = true, length = 40)
    private String codigo;

    @NotNull(message = "El tipo de comprobante es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoComprobante tipo;

    @NotNull(message = "La fecha de generación es obligatoria")
    @Column(name = "fecha_generacion", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime fechaGeneracion = LocalDateTime.now();

    @Column(name = "qr_base64", columnDefinition = "TEXT")
    private String qrBase64;

    @PrePersist
    public void prePersist() {
        if (this.fechaGeneracion == null) {
            this.fechaGeneracion = LocalDateTime.now();
        }
    }
}
