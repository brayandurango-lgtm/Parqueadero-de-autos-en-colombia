package com.autoscolombia.presentation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo de {@code PATCH /api/v1/clientes/{id}/activo}.
 *
 * <p>El estado es obligatorio: un cuerpo sin la propiedad o con {@code null} se rechaza con {@code 400}
 * en lugar de asumir un valor por defecto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CambiarEstadoClienteDTO {

    @NotNull(message = "El estado activo es obligatorio")
    private Boolean activo;
}
