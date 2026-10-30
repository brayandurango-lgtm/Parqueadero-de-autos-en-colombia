package com.autoscolombia.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum para los tipos de usuario en el sistema.
 */
public enum TipoUsuario {
    ADMINISTRADOR,
    OPERADOR;

    @JsonValue
    public String toValue() {
        return this.name();
    }
}
