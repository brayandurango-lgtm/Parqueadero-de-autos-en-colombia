package com.autoscolombia.presentation.dto;

import com.autoscolombia.domain.enums.TipoDocumento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entrada obligatoria para crear o editar un cliente.
 *
 * <p>Los máximos coinciden con las columnas de {@code V2__clientes_y_vinculo_vehiculos.sql}: 20 para
 * documento y teléfono, 120 para el nombre completo. El estado {@code activo} no viaja aquí: se cambia
 * exclusivamente con {@code PATCH /api/v1/clientes/{id}/activo}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteCrearDTO {

    @NotNull(message = "El tipo de documento es obligatorio")
    private TipoDocumento tipoDocumento;

    @NotBlank(message = "El documento es obligatorio")
    @Size(max = 20, message = "El documento no puede exceder 20 caracteres")
    private String documento;

    @NotBlank(message = "El nombre completo es obligatorio")
    @Size(max = 120, message = "El nombre completo no puede exceder 120 caracteres")
    private String nombreCompleto;

    @NotBlank(message = "El teléfono es obligatorio")
    @Size(max = 20, message = "El teléfono no puede exceder 20 caracteres")
    private String telefono;
}
