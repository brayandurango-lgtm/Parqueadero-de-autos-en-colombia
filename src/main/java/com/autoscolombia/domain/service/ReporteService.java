package com.autoscolombia.domain.service;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.EstadoRegistro;
import com.autoscolombia.domain.model.Registro;
import com.autoscolombia.infrastructure.repository.CeldaRepository;
import com.autoscolombia.infrastructure.repository.NovedadRepository;
import com.autoscolombia.infrastructure.repository.RegistroRepository;
import com.autoscolombia.presentation.dto.DashboardDTO;
import com.autoscolombia.presentation.dto.RegistroDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio para generación de reportes y dashboard.
 */
@Service
@RequiredArgsConstructor
public class ReporteService {

    private final RegistroRepository registroRepository;
    private final CeldaRepository celdaRepository;
    private final NovedadRepository novedadRepository;
    private final RegistroService registroService;

    /**
     * Obtiene los KPIs del dashboard.
     */
    public DashboardDTO obtenerKPIsDashboard() {
        // Celdas ocupadas y total
        long celdasOcupadas = celdaRepository.countByEstado(EstadoCelda.OCUPADA);
        long celdasTotal = celdaRepository.findAll().size();
        double porcentajeOcupacion = celdasTotal > 0 ? (celdasOcupadas * 100.0 / celdasTotal) : 0.0;

        // Vehículos del día
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        LocalDateTime finDia = LocalDate.now().atTime(23, 59, 59);
        long vehiculosHoy = registroRepository.countByFechaHoraEntradaBetween(inicioDia, finDia);

        // Novedades pendientes (de registros activos)
        List<Registro> registrosActivos = registroRepository.findByEstadoOrderByFechaHoraEntradaDesc(EstadoRegistro.ACTIVO);
        long novedadesPendientes = registrosActivos.stream()
                .flatMap(registro -> novedadRepository.findByRegistroIdOrderByFechaHoraDesc(registro.getId()).stream())
                .count();

        // Últimos 10 ingresos
        List<RegistroDTO> ultimosIngresos = registroService.listarUltimosIngresos(10).stream()
                .map(registroService::toDTO)
                .collect(Collectors.toList());

        return DashboardDTO.builder()
                .celdasOcupadas(celdasOcupadas)
                .celdasTotal(celdasTotal)
                .porcentajeOcupacion(Math.round(porcentajeOcupacion * 100.0) / 100.0)
                .vehiculosHoy(vehiculosHoy)
                .novedadesPendientes(novedadesPendientes)
                .ultimosIngresos(ultimosIngresos)
                .build();
    }

    /**
     * Genera reporte de ocupación en formato CSV.
     */
    public byte[] generarReporteOcupacion(LocalDate desde, LocalDate hasta) {
        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.atTime(23, 59, 59);

        List<Registro> registros = registroRepository.findByFechaHoraEntradaBetween(inicio, fin);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(outputStream, true, StandardCharsets.UTF_8);

        // Cabecera CSV
        writer.println("Placa,Marca,Modelo,Color,Propietario,Documento,Celda,Piso,Entrada,Salida,Duracion Minutos,Estado,Observaciones");

        // Formateador de fechas
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // Filas de datos
        for (Registro registro : registros) {
            String linea = String.format("%s,%s,%s,%s,%s,%s,%s,%d,%s,%s,%d,%s,%s",
                    registro.getVehiculo().getPlaca(),
                    registro.getVehiculo().getMarca(),
                    registro.getVehiculo().getModelo(),
                    registro.getVehiculo().getColor(),
                    registro.getVehiculo().getPropietarioNombre() != null ? registro.getVehiculo().getPropietarioNombre() : "",
                    registro.getVehiculo().getPropietarioDocumento() != null ? registro.getVehiculo().getPropietarioDocumento() : "",
                    registro.getCelda().getNumero(),
                    registro.getCelda().getPiso(),
                    registro.getFechaHoraEntrada().format(formatter),
                    registro.getFechaHoraSalida() != null ? registro.getFechaHoraSalida().format(formatter) : "",
                    registroService.calcularDuracion(registro).toMinutes(),
                    registro.getEstado(),
                    registro.getObservaciones() != null ? registro.getObservaciones() : ""
            );
            writer.println(linea);
        }

        writer.flush();
        return outputStream.toByteArray();
    }
}
