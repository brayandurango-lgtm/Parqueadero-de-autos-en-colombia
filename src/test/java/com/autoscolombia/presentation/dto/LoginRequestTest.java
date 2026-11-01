package com.autoscolombia.presentation.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRequestTest {

    @Test
    void toString_NoIncluyeLaContrasena() {
        LoginRequest request = LoginRequest.builder()
                .correo("admin@autoscolombia.com")
                .clave("ClaveSecreta12345*")
                .build();

        String resultado = request.toString();

        assertFalse(resultado.contains("ClaveSecreta12345*"), "El toString no debe contener la contraseña");
        assertTrue(resultado.contains("admin@autoscolombia.com"), "El toString debe contener el correo");
        assertTrue(resultado.contains("clave=***"), "El toString debe enmascarar la contraseña");
    }
}
