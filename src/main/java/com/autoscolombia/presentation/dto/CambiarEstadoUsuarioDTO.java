package com.autoscolombia.presentation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo de {@code PATCH /api/v1/usuarios/{id}/activo}. El estado es obligatorio: un valor ausente o
 * nulo se rechaza con 400 en lugar de interpretarse como activar o desactivar.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CambiarEstadoUsuarioDTO {

    @NotNull(message = "El estado activo es obligatorio")
    private Boolean activo;
}
