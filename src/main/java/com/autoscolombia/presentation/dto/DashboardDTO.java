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
    /** Celdas con estado OCUPADA, es decir, con un vehículo adentro. */
    private Long celdasOcupadas;

    /** Filas del inventario, incluidas las inactivas: capacidad histórica dada de alta. */
    private Long celdasTotal;

    /** Celdas con {@code activo = true}: las que hoy pueden usarse. */
    private Long celdasActivas;

    /** Celdas realmente asignables: activas, DISPONIBLES y sin registro ACTIVO. */
    private Long celdasDisponibles;

    /** Celdas en MANTENIMIENTO (siempre activas: el mantenimiento no se aplica a una celda inactiva). */
    private Long celdasMantenimiento;

    /** Porcentaje de ocupación sobre {@code celdasTotal}. */
    private Double porcentajeOcupacion;
    private Long vehiculosHoy;
    private Long novedadesPendientes;
    private List<RegistroDTO> ultimosIngresos;
}
