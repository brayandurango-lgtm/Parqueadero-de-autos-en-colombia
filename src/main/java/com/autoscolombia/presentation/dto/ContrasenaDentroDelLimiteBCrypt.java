package com.autoscolombia.presentation.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rechaza expresamente las contraseñas que superan el límite real de entrada del algoritmo BCrypt que
 * usa el proyecto.
 *
 * <p>Medido sobre {@code spring-security-crypto} 6.2.4: {@code BCryptPasswordEncoder} no lanza ninguna
 * excepción con entradas más largas; toma únicamente los primeros 72 bytes UTF-8. Dos claves distintas
 * que comparten ese prefijo producen el mismo hash y se aceptan entre sí, es decir, truncar en silencio
 * equivaldría a degradar la credencial sin avisar. Por eso el exceso se rechaza en lugar de recortarse.
 *
 * <p>El límite está expresado en <b>bytes</b> de la codificación UTF-8, no en caracteres: 36 "á" son
 * 72 bytes y caben; 37 "á" son 74 bytes y se rechazan. Un carácter puede ocupar entre 1 y 4 bytes, así
 * que una contraseña válida puede tener entre 15 y 72 caracteres según su contenido.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ContrasenaDentroDelLimiteBCryptValidator.class)
public @interface ContrasenaDentroDelLimiteBCrypt {

    /** Límite real de entrada medido en la implementación BCrypt utilizada. */
    int LIMITE_BYTES_UTF8 = 72;

    String message() default "La clave supera el límite de 72 bytes que admite el algoritmo de hash y no se trunca";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
