package com.autoscolombia.presentation.dto;

import com.autoscolombia.domain.enums.EstadoCelda;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Transición manual de estado operativo de una celda. El campo se tipa como enum para que un valor fuera de
 * los estados conocidos falle en la validación de entrada (400) y no dentro del servicio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CambiarEstadoCeldaDTO {

    @NotNull(message = "El estado de la celda es obligatorio")
    private EstadoCelda estado;
}
