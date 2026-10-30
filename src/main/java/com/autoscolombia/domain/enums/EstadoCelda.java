package com.autoscolombia.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum para los estados de una celda de parqueadero.
 */
public enum EstadoCelda {
    DISPONIBLE,
    OCUPADA,
    MANTENIMIENTO;

    @JsonValue
    public String toValue() {
        return this.name();
    }
}
