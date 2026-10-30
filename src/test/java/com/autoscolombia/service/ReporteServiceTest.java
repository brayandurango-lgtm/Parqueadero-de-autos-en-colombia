package com.autoscolombia.service;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.EstadoRegistro;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.Celda;
import com.autoscolombia.domain.model.Novedad;
import com.autoscolombia.domain.model.Registro;
import com.autoscolombia.domain.model.Vehiculo;
import com.autoscolombia.domain.service.RegistroService;
import com.autoscolombia.domain.service.ReporteService;
import com.autoscolombia.infrastructure.repository.CeldaRepository;
import com.autoscolombia.infrastructure.repository.NovedadRepository;
import com.autoscolombia.infrastructure.repository.RegistroRepository;
import com.autoscolombia.presentation.dto.RegistroDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    @Mock private RegistroRepository registroRepository;
    @Mock private CeldaRepository celdaRepository;
    @Mock private NovedadRepository novedadRepository;
    @Mock private RegistroService registroService;

    private ReporteService reporteService;

    private Registro registro;

    @BeforeEach
    void setUp() {
        reporteService = new ReporteService(registroRepository, celdaRepository, novedadRepository, registroService);
        Vehiculo vehiculo = Vehiculo.builder().placa("ABC123").marca("Mazda").modelo("3")
                .color("Rojo").tipo(TipoVehiculo.CARRO).propietarioNombre("Ana Pérez")
                .propietarioDocumento("12345678").build();
        Celda celda = Celda.builder().numero("A-01").piso(1).tipoVehiculo(TipoVehiculo.CARRO)
                .estado(EstadoCelda.OCUPADA).build();
        registro = Registro.builder().id(1L).vehiculo(vehiculo).celda(celda)
                .fechaHoraEntrada(LocalDateTime.of(2026, 10, 19, 10, 0))
                .estado(EstadoRegistro.ACTIVO).observaciones("Ingreso normal").build();
    }

    @Test
    void dashboardCalculaIndicadoresYMapeaUltimosIngresos() {
        when(celdaRepository.countByEstado(EstadoCelda.OCUPADA)).thenReturn(2L);
        when(celdaRepository.findAll()).thenReturn(List.of(new Celda(), new Celda(), new Celda(), new Celda()));
        when(registroRepository.countByFechaHoraEntradaBetween(any(), any())).thenReturn(3L);
        when(registroRepository.findByEstadoOrderByFechaHoraEntradaDesc(EstadoRegistro.ACTIVO)).thenReturn(List.of(registro));
        when(novedadRepository.findByRegistroIdOrderByFechaHoraDesc(anyLong())).thenReturn(List.of(new Novedad(), new Novedad()));
        when(registroService.listarUltimosIngresos(10)).thenReturn(List.of(registro));
        when(registroService.toDTO(registro)).thenReturn(RegistroDTO.builder().id(1L).placa("ABC123").build());

        var dashboard = reporteService.obtenerKPIsDashboard();

        assertEquals(2L, dashboard.getCeldasOcupadas());
        assertEquals(4L, dashboard.getCeldasTotal());
        assertEquals(50.0, dashboard.getPorcentajeOcupacion());
        assertEquals(3L, dashboard.getVehiculosHoy());
        assertEquals(2L, dashboard.getNovedadesPendientes());
        assertEquals("ABC123", dashboard.getUltimosIngresos().get(0).getPlaca());
        verify(registroService).listarUltimosIngresos(10);
    }

    @Test
    void dashboardSinCeldasUsaPorcentajeCeroYSinDatos() {
        when(celdaRepository.countByEstado(EstadoCelda.OCUPADA)).thenReturn(0L);
        when(celdaRepository.findAll()).thenReturn(List.of());
        when(registroRepository.countByFechaHoraEntradaBetween(any(), any())).thenReturn(0L);
        when(registroRepository.findByEstadoOrderByFechaHoraEntradaDesc(EstadoRegistro.ACTIVO)).thenReturn(List.of());
        when(registroService.listarUltimosIngresos(10)).thenReturn(List.of());

        var dashboard = reporteService.obtenerKPIsDashboard();

        assertEquals(0.0, dashboard.getPorcentajeOcupacion());
        assertEquals(0L, dashboard.getNovedadesPendientes());
        assertTrue(dashboard.getUltimosIngresos().isEmpty());
        verifyNoInteractions(novedadRepository);
    }

    @Test
    void reporteCsvIncluyeCabeceraRegistroActivoYDuracion() {
        when(registroRepository.findByFechaHoraEntradaBetween(any(), any())).thenReturn(List.of(registro));
        when(registroService.calcularDuracion(registro)).thenReturn(java.time.Duration.ofMinutes(45));

        String csv = new String(reporteService.generarReporteOcupacion(
                LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 19)), StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("Placa,Marca,Modelo,Color,Propietario"));
        assertTrue(csv.contains("ABC123,Mazda,3,Rojo,Ana Pérez,12345678,A-01,1"));
        assertTrue(csv.contains("2026-10-19 10:00:00,,45,ACTIVO,Ingreso normal"));
        verify(registroRepository).findByFechaHoraEntradaBetween(
                LocalDate.of(2026, 10, 19).atStartOfDay(), LocalDate.of(2026, 10, 19).atTime(23, 59, 59));
    }

    @Test
    void reporteCsvIncluyeSalidaYCeldasYDatosOpcionalesNulos() {
        registro.setEstado(EstadoRegistro.CERRADO);
        registro.setFechaHoraSalida(LocalDateTime.of(2026, 10, 19, 10, 30));
        registro.setObservaciones(null);
        registro.getVehiculo().setPropietarioNombre(null);
        registro.getVehiculo().setPropietarioDocumento(null);
        when(registroRepository.findByFechaHoraEntradaBetween(any(), any())).thenReturn(List.of(registro));
        when(registroService.calcularDuracion(registro)).thenReturn(java.time.Duration.ofMinutes(30));

        String csv = new String(reporteService.generarReporteOcupacion(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)), StandardCharsets.UTF_8);

        assertTrue(csv.contains("ABC123,Mazda,3,Rojo,,,A-01,1"));
        assertTrue(csv.contains("2026-10-19 10:00:00,2026-10-19 10:30:00,30,CERRADO,"));
        verify(registroService).calcularDuracion(registro);
    }

    @Test
    void reporteCsvVacioContieneSoloCabecera() {
        when(registroRepository.findByFechaHoraEntradaBetween(any(), any())).thenReturn(List.of());

        String csv = new String(reporteService.generarReporteOcupacion(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)), StandardCharsets.UTF_8);

        assertEquals(1, csv.lines().count());
        verifyNoInteractions(registroService);
    }
}
