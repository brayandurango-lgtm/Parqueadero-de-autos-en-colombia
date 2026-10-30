package com.autoscolombia.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum para los tipos de vehículos.
 */
public enum TipoVehiculo {
    CARRO,
    MOTO,
    CAMIONETA;

    @JsonValue
    public String toValue() {
        return this.name();
    }
}
