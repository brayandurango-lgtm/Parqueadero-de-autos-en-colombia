package com.autoscolombia.presentation.controllers;

import com.autoscolombia.presentation.dto.DashboardDTO;
import com.autoscolombia.domain.service.ReporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Controlador REST para generación de reportes y dashboard.
 */
@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
@Tag(name = "Reportes", description = "Operaciones de generación de reportes y dashboard")
public class ReporteController {

    private final ReporteService reporteService;

    @GetMapping("/dashboard")
    @Operation(summary = "Obtener dashboard", description = "Retorna los KPIs del sistema")
    public ResponseEntity<DashboardDTO> obtenerDashboard() {
        DashboardDTO dashboard = reporteService.obtenerKPIsDashboard();
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/ocupacion")
    @Operation(summary = "Reporte de ocupación", description = "Genera reporte de ocupación en formato CSV")
    public ResponseEntity<byte[]> generarReporteOcupacion(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        byte[] csv = reporteService.generarReporteOcupacion(desde, hasta);

        String filename = "reporte_ocupacion_" + desde + "_" + hasta + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
