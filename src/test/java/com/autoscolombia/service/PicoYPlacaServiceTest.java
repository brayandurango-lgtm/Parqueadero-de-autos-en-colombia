package com.autoscolombia.service;

import com.autoscolombia.domain.service.PicoYPlacaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class PicoYPlacaServiceTest {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    private PicoYPlacaService picoYPlacaService;

    private static PicoYPlacaService conRelojEn(LocalDateTime fechaHora) {
        return new PicoYPlacaService(
                Clock.fixed(fechaHora.atZone(ZONA_BOGOTA).toInstant(), ZONA_BOGOTA));
    }

    @BeforeEach
    void setUp() {
        // Miércoles 4 de noviembre de 2026: día sin restricción para el dígito 7
        picoYPlacaService = conRelojEn(LocalDateTime.of(2026, Month.NOVEMBER, 4, 10, 0));
    }

    @Test
    void testRelojInyectado_DiaPermitido_ConPlacaTerminadaEn7() {
        assertTrue(picoYPlacaService.puedeTransitar("XYZ987"));
        assertNull(picoYPlacaService.obtenerMensajeRestriccion("XYZ987"));
    }

    @Test
    void testRelojInyectado_DiaRestringido_ConPlacaTerminadaEn7() {
        // Viernes 30 de octubre de 2026: la regla vigente restringe 7 y 8
        PicoYPlacaService servicioViernes =
                conRelojEn(LocalDateTime.of(2026, Month.OCTOBER, 30, 10, 0));

        assertFalse(servicioViernes.puedeTransitar("XYZ987"));

        String mensaje = servicioViernes.obtenerMensajeRestriccion("XYZ987");
        assertNotNull(mensaje);
        assertTrue(mensaje.contains("XYZ987"));
        assertTrue(mensaje.contains("Viernes"));
    }

    @Test
    void testLunes_Restringido_Digito6() {
        LocalDateTime lunes = LocalDateTime.of(2026, Month.JANUARY, 5, 10, 0); // Lunes
        assertFalse(picoYPlacaService.puedeTransitar("ABC126", lunes));
    }

    @Test
    void testLunes_Restringido_Digito9() {
        LocalDateTime lunes = LocalDateTime.of(2026, Month.JANUARY, 5, 10, 0); // Lunes
        assertFalse(picoYPlacaService.puedeTransitar("ABC129", lunes));
    }

    @Test
    void testLunes_Permite_OtrosDigitos() {
        LocalDateTime lunes = LocalDateTime.of(2026, Month.JANUARY, 5, 10, 0); // Lunes
        assertTrue(picoYPlacaService.puedeTransitar("ABC123", lunes));
    }

    @Test
    void testLunes_DigitoPermitido() {
        LocalDateTime lunes = LocalDateTime.of(2026, Month.JANUARY, 5, 10, 0);
        assertTrue(picoYPlacaService.puedeTransitar("ABC125", lunes));
    }

    @Test
    void testMartes_Restringido_Digito0() {
        LocalDateTime martes = LocalDateTime.of(2026, Month.JANUARY, 6, 10, 0); // Martes
        assertFalse(picoYPlacaService.puedeTransitar("ABC120", martes));
    }

    @Test
    void testMartes_Restringido_Digito1() {
        LocalDateTime martes = LocalDateTime.of(2026, Month.JANUARY, 6, 10, 0); // Martes
        assertFalse(picoYPlacaService.puedeTransitar("ABC121", martes));
    }

    @Test
    void testMiercoles_Restringido_Digito2() {
        LocalDateTime miercoles = LocalDateTime.of(2026, Month.JANUARY, 7, 10, 0); // Miércoles
        assertFalse(picoYPlacaService.puedeTransitar("ABC122", miercoles));
    }

    @Test
    void testMiercoles_Restringido_Digito3() {
        LocalDateTime miercoles = LocalDateTime.of(2026, Month.JANUARY, 7, 10, 0); // Miércoles
        assertFalse(picoYPlacaService.puedeTransitar("ABC123", miercoles));
    }

    @Test
    void testJueves_Restringido_Digito4() {
        LocalDateTime jueves = LocalDateTime.of(2026, Month.JANUARY, 8, 10, 0); // Jueves
        assertFalse(picoYPlacaService.puedeTransitar("ABC124", jueves));
    }

    @Test
    void testJueves_Restringido_Digito5() {
        LocalDateTime jueves = LocalDateTime.of(2026, Month.JANUARY, 8, 10, 0); // Jueves
        assertFalse(picoYPlacaService.puedeTransitar("ABC125", jueves));
    }

    @Test
    void testViernes_Restringido_Digito7() {
        LocalDateTime viernes = LocalDateTime.of(2026, Month.JANUARY, 9, 10, 0); // Viernes (9 de enero de 2026)
        assertFalse(picoYPlacaService.puedeTransitar("ABC127", viernes));
    }

    @Test
    void testViernes_Restringido_Digito8() {
        LocalDateTime viernes = LocalDateTime.of(2026, Month.JANUARY, 9, 10, 0); // Viernes (9 de enero de 2026)
        assertFalse(picoYPlacaService.puedeTransitar("ABC128", viernes));
    }

    @Test
    void testSabado_NoRestringe() {
        LocalDateTime sabado = LocalDateTime.of(2026, Month.JANUARY, 11, 10, 0); // Sábado
        assertTrue(picoYPlacaService.puedeTransitar("ABC126", sabado));
    }

    @Test
    void testDomingo_NoRestringe() {
        LocalDateTime domingo = LocalDateTime.of(2026, Month.JANUARY, 12, 10, 0); // Domingo
        assertTrue(picoYPlacaService.puedeTransitar("ABC120", domingo));
    }

    @Test
    void testObtenerMensajeRestriccion_Restringido() {
        LocalDateTime lunes = LocalDateTime.of(2026, Month.JANUARY, 5, 10, 0);
        String mensaje = picoYPlacaService.obtenerMensajeRestriccion("ABC126", lunes);
        assertNotNull(mensaje);
        assertTrue(mensaje.contains("Lunes"));
        assertTrue(mensaje.contains("ABC126"));
    }

    @Test
    void testObtenerMensajeRestriccion_NoRestringido() {
        LocalDateTime lunes = LocalDateTime.of(2026, Month.JANUARY, 5, 10, 0);
        String mensaje = picoYPlacaService.obtenerMensajeRestriccion("ABC123", lunes);
        assertNull(mensaje);
    }

    @Test
    void testPlacaMotoConLetraFinalNoRestringePorDigitoNoNumerico() {
        LocalDateTime lunes = LocalDateTime.of(2026, Month.JANUARY, 5, 10, 0);
        assertTrue(picoYPlacaService.puedeTransitar("AB123C", lunes));
    }

    @Test
    void testDiaHabilAntesDeHorarioRestriccionActualmenteAplicaReglaDelDia() {
        LocalDateTime temprano = LocalDateTime.of(2026, Month.JANUARY, 5, 5, 30);
        assertFalse(picoYPlacaService.puedeTransitar("ABC126", temprano));
    }
}
