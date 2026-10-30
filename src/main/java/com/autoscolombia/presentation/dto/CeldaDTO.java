package com.autoscolombia.presentation.dto;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.TipoVehiculo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para celda de parqueadero.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CeldaDTO {
    private Long id;
    private String numero;
    private Integer piso;
    private TipoVehiculo tipoVehiculo;
    private EstadoCelda estado;
    private String observaciones;
}
