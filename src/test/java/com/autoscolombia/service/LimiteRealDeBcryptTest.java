package com.autoscolombia.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fija el comportamiento real del límite de entrada de BCrypt medido en este proyecto
 * ({@code spring-security-crypto} 6.2.4). Es la evidencia que justifica rechazar el exceso en lugar de
 * confiar en el algoritmo: la librería no lanza ninguna excepción con más de 72 bytes, simplemente los
 * ignora.
 */
class LimiteRealDeBcryptTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void elLimiteRealSonSetentaDosBytesUtf8YNoSetentaDosCaracteres() {
        String agudosEnElLimite = "á".repeat(36);
        assertEquals(36, agudosEnElLimite.length());
        assertEquals(72, agudosEnElLimite.getBytes(StandardCharsets.UTF_8).length);

        String agudosExcedido = "á".repeat(37);
        assertEquals(74, agudosExcedido.getBytes(StandardCharsets.UTF_8).length);

        assertTrue(encoder.matches(agudosEnElLimite, encoder.encode(agudosEnElLimite)));
        assertTrue(encoder.matches(agudosExcedido, encoder.encode(agudosExcedido)));
    }

    @Test
    void bcryptNoRechazaElExcesoLoTruncaEnSilencio() {
        String prefijoCompartido = "a".repeat(72);
        String claveLarga = prefijoCompartido + "SUFIJO_UNO";
        String otraClaveLarga = prefijoCompartido + "SUFIJO_DOS";

        String hash = encoder.encode(claveLarga);

        assertTrue(encoder.matches(otraClaveLarga, hash),
                "Dos contraseñas distintas que comparten los primeros 72 bytes validan entre las dos: "
                        + "la librería truncó la entrada sin avisar");
        assertFalse(encoder.matches("b".repeat(72) + "SUFIJO_UNO", hash),
                "Control: una diferencia dentro de los primeros 72 bytes sí cambia la credencial");
    }

    @Test
    void unaClaveDeSetentaytresBytesNoProvocaNingunaExcepcion() {
        String excesiva = "a".repeat(73);
        assertEquals(73, excesiva.getBytes(StandardCharsets.UTF_8).length);

        String hash = encoder.encode(excesiva);

        assertTrue(encoder.matches(excesiva, hash), "La validación sigue siendo correcta, pero sobre 72 bytes");
    }
}
