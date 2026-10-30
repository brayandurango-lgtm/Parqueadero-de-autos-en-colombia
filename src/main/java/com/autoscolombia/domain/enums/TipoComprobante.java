package com.autoscolombia.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum para los tipos de comprobantes.
 */
public enum TipoComprobante {
    ENTRADA,
    SALIDA;

    @JsonValue
    public String toValue() {
        return this.name();
    }
}
