package com.autoscolombia.presentation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Configuración del reloj usado por las reglas que dependen del calendario.
 *
 * {@code Clock.systemDefaultZone()} conserva exactamente el comportamiento temporal
 * anterior: la aplicación no fijaba ninguna zona y leía {@code LocalDateTime.now()},
 * que resuelve la zona por defecto de la JVM. En la operación local en Colombia eso
 * corresponde a America/Bogota (-05:00), pero la zona NO queda fijada en código: la
 * define el entorno de ejecución (en CI, ubuntu-latest, sería UTC).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
