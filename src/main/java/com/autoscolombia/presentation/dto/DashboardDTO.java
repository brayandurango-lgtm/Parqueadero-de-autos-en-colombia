package com.autoscolombia.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO para dashboard con KPIs del sistema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDTO {
    private Long celdasOcupadas;
    private Long celdasTotal;
    private Double porcentajeOcupacion;
    private Long vehiculosHoy;
    private Long novedadesPendientes;
    private List<RegistroDTO> ultimosIngresos;
}
