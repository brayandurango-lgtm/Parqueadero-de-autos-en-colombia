package com.autoscolombia.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum para los estados de un registro de parqueadero.
 */
public enum EstadoRegistro {
    ACTIVO,
    CERRADO,
    CANCELADO;

    @JsonValue
    public String toValue() {
        return this.name();
    }
}
