package com.autoscolombia.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Clasificación interna del documento de un cliente.
 *
 * <p>Es una clasificación propuesta por el proyecto para poder distinguir dos personas que compartan
 * número de documento. <strong>No</strong> es una validación oficial de identidad: no se implementan
 * dígitos de verificación ni reglas por tipo de documento.
 */
public enum TipoDocumento {
    CC,
    CE,
    TI,
    PASAPORTE,
    OTRO;

    @JsonValue
    public String toValue() {
        return this.name();
    }
}
