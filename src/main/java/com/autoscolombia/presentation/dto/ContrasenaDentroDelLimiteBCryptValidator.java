package com.autoscolombia.presentation.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/**
 * Verificador de {@link ContrasenaDentroDelLimiteBCrypt}. Nulos y vacíos los resuelven otras anotaciones;
 * aquí solo importa el tamaño en bytes UTF-8.
 */
public class ContrasenaDentroDelLimiteBCryptValidator
        implements ConstraintValidator<ContrasenaDentroDelLimiteBCrypt, String> {

    @Override
    public boolean isValid(String clave, ConstraintValidatorContext context) {
        if (clave == null) {
            return true;
        }
        return clave.getBytes(StandardCharsets.UTF_8).length
                <= ContrasenaDentroDelLimiteBCrypt.LIMITE_BYTES_UTF8;
    }
}
