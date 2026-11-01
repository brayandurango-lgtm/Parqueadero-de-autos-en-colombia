package com.autoscolombia.presentation.dto;

import com.autoscolombia.domain.enums.TipoUsuario;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Política de contraseña inicial y forma de los DTO de cuenta del personal, verificadas directamente
 * sobre el validador de Bean Validation (las mismas anotaciones que ejecuta la capa HTTP).
 */
class ValidacionContrasenaDeCuentaTest {

    private static ValidatorFactory fabrica;
    private static Validator validator;

    @BeforeAll
    static void abrirValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validator = fabrica.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        fabrica.close();
    }

    private UsuarioCrearDTO solicitudConClave(String clave) {
        return UsuarioCrearDTO.builder()
                .nombre("Ana").apellido("Pérez").documento("12345678").correo("ana@example.com")
                .telefono("3001112233").tipo(TipoUsuario.OPERADOR).clave(clave).build();
    }

    private Set<String> mensajesDe(UsuarioCrearDTO solicitud) {
        return validator.validate(solicitud).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    // ------------------------------------------------------------------ mínimo 15 caracteres

    @Test
    void claveDeQuinceCaracteresPasaSinViolaciones() {
        assertTrue(mensajesDe(solicitudConClave("a".repeat(15))).isEmpty());
    }

    @Test
    void claveDeCatorceCaracteresSeRechazaPorLongitudMinima() {
        Set<String> mensajes = mensajesDe(solicitudConClave("a".repeat(14)));
        assertTrue(mensajes.stream().anyMatch(m -> m.contains("15 caracteres")), mensajes.toString());
    }

    @Test
    void claveAusenteVaciaOSoloEspaciosSeRechazaComoObligatoria() {
        assertTrue(mensajesDe(solicitudConClave(null)).stream().anyMatch(m -> m.contains("obligatoria")));
        assertTrue(mensajesDe(solicitudConClave("")).stream().anyMatch(m -> m.contains("obligatoria")));
        assertTrue(mensajesDe(solicitudConClave("                 ")).stream()
                .anyMatch(m -> m.contains("obligatoria")));
    }

    // ------------------------------------------------------------------ máximo 72 bytes UTF-8

    @Test
    void claveDeSetentaDosBytesAsciiPasa() {
        String limite = "a".repeat(72);
        assertEquals(72, limite.getBytes(StandardCharsets.UTF_8).length);
        assertTrue(mensajesDe(solicitudConClave(limite)).isEmpty());
    }

    @Test
    void claveDeSetentaYDosCaracteresMultibyteQueOcupan72BytesPasa() {
        String agudos = "á".repeat(36);
        assertEquals(36, agudos.length(), "36 caracteres");
        assertEquals(72, agudos.getBytes(StandardCharsets.UTF_8).length, "72 bytes exactos");
        assertTrue(mensajesDe(solicitudConClave(agudos)).isEmpty());
    }

    @Test
    void claveQuePasaElLimiteDeBytesSeRechazaConNombreDelCampoYMensajeSeguro() {
        String agudos = "á".repeat(37);
        assertEquals(74, agudos.getBytes(StandardCharsets.UTF_8).length);

        Set<ConstraintViolation<UsuarioCrearDTO>> violaciones = validator.validate(solicitudConClave(agudos));

        assertEquals(1, violaciones.size(), violaciones.toString());
        ConstraintViolation<UsuarioCrearDTO> unica = violaciones.iterator().next();
        assertEquals("clave", unica.getPropertyPath().toString());
        assertTrue(unica.getMessage().contains("72 bytes"), unica.getMessage());
        assertFalse(unica.getMessage().contains(agudos), "El mensaje no repite la contraseña rechazada");
    }

    @Test
    void caracteresDeCuatroBytesSeMidenTambienEnBytes() {
        String emojis = "\uD83D\uDE00".repeat(20);
        assertEquals(40, emojis.length(), "20 pictogramas ocupan 40 unidades UTF-16, que es lo que cuenta @Size");
        assertEquals(80, emojis.getBytes(StandardCharsets.UTF_8).length, "y 80 bytes UTF-8");

        assertTrue(mensajesDe(solicitudConClave(emojis)).stream().anyMatch(m -> m.contains("72 bytes")));
    }

    @Test
    void espaciosInicialesYFinalesNoSeRecortanNImpidenUnaClaveValida() {
        String conEspacios = "  Clave con espacios  y ñ  ";
        Set<String> mensajes = mensajesDe(solicitudConClave(conEspacios));

        assertTrue(mensajes.isEmpty(), mensajes.toString());
        assertEquals(conEspacios, solicitudConClave(conEspacios).getClave());
    }

    // ------------------------------------------------------------------ filtrado de la contraseña

    @Test
    void elToStringDeLaSolicitudDeCreacionNoRevelaLaClave() {
        String clavePrueba = "Clave secreta de prueba 9";
        String texto = solicitudConClave(clavePrueba).toString();

        assertFalse(texto.contains(clavePrueba), texto);
        assertTrue(texto.contains("ana@example.com"), "El resto del perfil sí se muestra para depuración");
    }

    @Test
    void elUnicoDtoQueTransportaLaClaveEsElDeCreacion() {
        assertEquals(Set.of("id", "nombre", "apellido", "documento", "correo", "telefono", "tipo", "activo"),
                nombresDeCampos(UsuarioDTO.class));
        assertTrue(nombresDeCampos(UsuarioCrearDTO.class).contains("clave"),
                "Solo el DTO de creación transporta la contraseña");
    }

    private Set<String> nombresDeCampos(Class<?> tipo) {
        return java.util.Arrays.stream(tipo.getDeclaredFields()).map(Field::getName).collect(Collectors.toSet());
    }

    // ------------------------------------------------------------------ cuerpo del cambio de estado

    @Test
    void cambiarEstadoRechazaElEstadoAusenteONulo() {
        Set<ConstraintViolation<CambiarEstadoUsuarioDTO>> nulo =
                validator.validate(CambiarEstadoUsuarioDTO.builder().activo(null).build());
        assertEquals(1, nulo.size());
        assertTrue(nulo.iterator().next().getMessage().contains("obligatorio"));

        assertTrue(validator.validate(CambiarEstadoUsuarioDTO.builder().activo(false).build()).isEmpty());
        assertTrue(validator.validate(CambiarEstadoUsuarioDTO.builder().activo(true).build()).isEmpty());
    }
}
