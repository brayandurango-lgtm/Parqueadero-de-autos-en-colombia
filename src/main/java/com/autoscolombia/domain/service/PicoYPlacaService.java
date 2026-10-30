package com.autoscolombia.domain.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;

/**
 * Servicio para validación de Pico y Placa en Bogotá D.C.
 * Reglas:
 * - Lunes: placas terminadas en 6 y 9
 * - Martes: placas terminadas en 0 y 1
 * - Miércoles: placas terminadas en 2 y 3
 * - Jueves: placas terminadas en 4 y 5
 * - Viernes: placas terminadas en 7 y 8
 * - Sábado, domingo y festivos: no aplica
 * - Horario restricción: 6:00-20:00 (bloqueo todo el día en esta implementación)
 *
 * Limitaciones preexistentes de la regla (no introducidas por este cambio):
 * está codificada para Bogotá D.C., no aplica festivos y bloquea el día completo
 * sin respetar la franja horaria de 6:00-20:00 que menciona su propia documentación.
 */
@Service
@RequiredArgsConstructor
public class PicoYPlacaService {

    private final Clock clock;

    /**
     * Valida si la placa puede ingresar en la fecha indicada.
     *
     * @param placa    Placa del vehículo
     * @param fechaHora Fecha y hora a validar
     * @return true si puede transitar, false si está restringido
     */
    public boolean puedeTransitar(String placa, LocalDateTime fechaHora) {
        // Sábado y domingo no aplica restricción
        DayOfWeek diaSemana = fechaHora.getDayOfWeek();
        if (diaSemana == DayOfWeek.SATURDAY || diaSemana == DayOfWeek.SUNDAY) {
            return true;
        }

        // Obtener el último dígito de la placa
        char ultimoDigito = placa.charAt(placa.length() - 1);
        int digito = Character.getNumericValue(ultimoDigito);

        // Validar según el día de la semana
        return switch (diaSemana) {
            case MONDAY -> digito != 6 && digito != 9;
            case TUESDAY -> digito != 0 && digito != 1;
            case WEDNESDAY -> digito != 2 && digito != 3;
            case THURSDAY -> digito != 4 && digito != 5;
            case FRIDAY -> digito != 7 && digito != 8;
            default -> true;
        };
    }

    /**
     * Obtiene el mensaje de restricción si aplica.
     *
     * @param placa    Placa del vehículo
     * @param fechaHora Fecha y hora a validar
     * @return Mensaje de restricción o null si no aplica
     */
    public String obtenerMensajeRestriccion(String placa, LocalDateTime fechaHora) {
        if (puedeTransitar(placa, fechaHora)) {
            return null;
        }

        DayOfWeek diaSemana = fechaHora.getDayOfWeek();
        String nombreDia = switch (diaSemana) {
            case MONDAY -> "Lunes";
            case TUESDAY -> "Martes";
            case WEDNESDAY -> "Miércoles";
            case THURSDAY -> "Jueves";
            case FRIDAY -> "Viernes";
            default -> "";
        };

        return String.format("El vehículo con placa %s no puede circular el día %s por restricción de Pico y Placa", placa, nombreDia);
    }

    /**
     * Valida si la placa puede ingresar en el momento actual según el reloj inyectado.
     * Zona horaria: la del reloj configurado (en producción, la zona del sistema,
     * idéntica a la que usaba {@code LocalDateTime.now()} antes de este cambio).
     *
     * @param placa Placa del vehículo
     * @return true si puede transitar ahora, false si está restringido
     */
    public boolean puedeTransitar(String placa) {
        return puedeTransitar(placa, LocalDateTime.now(clock));
    }

    /**
     * Obtiene el mensaje de restricción para el momento actual según el reloj inyectado.
     *
     * @param placa Placa del vehículo
     * @return Mensaje de restricción o null si no aplica
     */
    public String obtenerMensajeRestriccion(String placa) {
        return obtenerMensajeRestriccion(placa, LocalDateTime.now(clock));
    }
}
