package com.autoscolombia.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para registro de salida de vehículo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistroSalidaDTO {
    private String observaciones;
}
