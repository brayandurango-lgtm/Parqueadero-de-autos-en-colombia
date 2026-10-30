package com.autoscolombia.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum para los tipos de novedades que pueden reportarse.
 */
public enum TipoNovedad {
    RAYON,
    DANO_MECANICO,
    OBJETO_CAIDO,
    ROBO_PARCIAL,
    OTRO;

    @JsonValue
    public String toValue() {
        return this.name();
    }
}
