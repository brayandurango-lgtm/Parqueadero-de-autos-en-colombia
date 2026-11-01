package com.autoscolombia.presentation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Alta y baja lógica de una celda. Es estado lógico, no estado operativo: no puede poner la celda OCUPADA ni
 * liberarla.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CambiarActivoCeldaDTO {

    @NotNull(message = "El estado activo es obligatorio")
    private Boolean activo;
}
