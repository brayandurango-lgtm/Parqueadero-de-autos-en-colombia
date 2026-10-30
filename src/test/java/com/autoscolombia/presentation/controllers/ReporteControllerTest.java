package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.service.ReporteService;
import com.autoscolombia.presentation.dto.DashboardDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReporteControllerTest {
    @Mock private ReporteService reporteService;
    @InjectMocks private ReporteController controller;

    @Test
    void obtenerDashboardDevuelveMetricas() {
        DashboardDTO dto = DashboardDTO.builder().celdasTotal(10L).celdasOcupadas(4L).porcentajeOcupacion(40.0).build();
        when(reporteService.obtenerKPIsDashboard()).thenReturn(dto);
        var response = controller.obtenerDashboard();
        assertSame(dto, response.getBody());
        verify(reporteService).obtenerKPIsDashboard();
    }

    @Test
    void exportarReporteDevuelveCsvYNombreDeArchivo() {
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 10, 31);
        byte[] csv = "Placa\nABC123\n".getBytes(StandardCharsets.UTF_8);
        when(reporteService.generarReporteOcupacion(desde, hasta)).thenReturn(csv);

        var response = controller.generarReporteOcupacion(desde, hasta);

        assertArrayEquals(csv, response.getBody());
        assertEquals("text/csv", response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("reporte_ocupacion_2026-10-01_2026-10-31.csv"));
        verify(reporteService).generarReporteOcupacion(desde, hasta);
    }
}
