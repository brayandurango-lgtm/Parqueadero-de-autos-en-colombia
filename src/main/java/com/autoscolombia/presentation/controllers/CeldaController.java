package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.Celda;
import com.autoscolombia.domain.service.CeldaService;
import com.autoscolombia.presentation.dto.CeldaDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controlador REST para gestión de celdas de parqueadero.
 */
@RestController
@RequestMapping("/api/v1/celdas")
@RequiredArgsConstructor
@Tag(name = "Celdas", description = "Operaciones de gestión de celdas")
public class CeldaController {

    private final CeldaService celdaService;

    @GetMapping
    @Operation(summary = "Listar celdas", description = "Retorna todas las celdas con filtros opcionales")
    public ResponseEntity<List<CeldaDTO>> listar(
            @RequestParam(required = false) EstadoCelda estado,
            @RequestParam(required = false) TipoVehiculo tipo,
            @RequestParam(required = false) Integer piso) {
        List<Celda> celdas = celdaService.obtenerTodas();

        if (estado != null) {
            celdas = celdas.stream().filter(c -> c.getEstado() == estado).collect(Collectors.toList());
        }
        if (tipo != null) {
            celdas = celdas.stream().filter(c -> c.getTipoVehiculo() == tipo).collect(Collectors.toList());
        }
        if (piso != null) {
            celdas = celdas.stream().filter(c -> c.getPiso().equals(piso)).collect(Collectors.toList());
        }

        List<CeldaDTO> dtos = celdas.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/disponibles")
    @Operation(summary = "Listar celdas disponibles", description = "Retorna las celdas en estado DISPONIBLE, filtrables por tipo de vehículo")
    public ResponseEntity<List<CeldaDTO>> listarDisponibles(
            @RequestParam(required = false) TipoVehiculo tipo) {
        List<Celda> celdas = celdaService.obtenerTodas().stream()
                .filter(c -> c.getEstado() == EstadoCelda.DISPONIBLE)
                .filter(c -> tipo == null || c.getTipoVehiculo() == tipo)
                .collect(Collectors.toList());

        List<CeldaDTO> dtos = celdas.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener celda por ID", description = "Retorna una celda específica")
    public ResponseEntity<CeldaDTO> obtenerPorId(@PathVariable Long id) {
        Celda celda = celdaService.obtenerPorId(id);
        return ResponseEntity.ok(toDTO(celda));
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Cambiar estado de celda", description = "Cambia el estado de una celda (solo administrador)")
    public ResponseEntity<CeldaDTO> cambiarEstado(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        EstadoCelda nuevoEstado = EstadoCelda.valueOf(request.get("estado"));
        Celda celda = celdaService.cambiarEstado(id, nuevoEstado);
        return ResponseEntity.ok(toDTO(celda));
    }

    private CeldaDTO toDTO(Celda celda) {
        return CeldaDTO.builder()
                .id(celda.getId())
                .numero(celda.getNumero())
                .piso(celda.getPiso())
                .tipoVehiculo(celda.getTipoVehiculo())
                .estado(celda.getEstado())
                .observaciones(celda.getObservaciones())
                .build();
    }
}
