package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.Celda;
import com.autoscolombia.domain.service.CeldaService;
import com.autoscolombia.presentation.dto.CambiarActivoCeldaDTO;
import com.autoscolombia.presentation.dto.CambiarEstadoCeldaDTO;
import com.autoscolombia.presentation.dto.CeldaActualizarDTO;
import com.autoscolombia.presentation.dto.CeldaCrearDTO;
import com.autoscolombia.presentation.dto.CeldaDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controlador REST para gestión de celdas de parqueadero.
 *
 * <p>Consultar está abierto a ADMINISTRADOR y OPERADOR. Crear, editar, cambiar el estado operativo y
 * activar o desactivar es solo de ADMINISTRADOR, y la regla de fondo no vive aquí: {@link CeldaService} la
 * aplica otra vez sobre la fila bloqueada, así que ocultar el botón en la pantalla no concede permiso.
 *
 * <p>{@code GET /celdas} conserva su contrato de array porque lo consume la operación existente; la pantalla
 * de administración usa {@code GET /celdas/pagina}, que devuelve {@code Page} y admite todos los filtros.
 */
@RestController
@RequestMapping("/api/v1/celdas")
@RequiredArgsConstructor
@Tag(name = "Celdas", description = "Operaciones de gestión de celdas")
public class CeldaController {

    private static final int TAMANO_MAXIMO_PAGINA = 100;

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

        return ResponseEntity.ok(celdas.stream().map(celdaService::aDTO).collect(Collectors.toList()));
    }

    @GetMapping("/pagina")
    @Operation(summary = "Listar celdas para administración",
            description = "Lista paginada con filtros por código, estado operativo, tipo, piso y estado lógico")
    public ResponseEntity<Page<CeldaDTO>> listarPagina(
            @RequestParam(required = false) String criterio,
            @RequestParam(required = false) EstadoCelda estado,
            @RequestParam(required = false) TipoVehiculo tipo,
            @RequestParam(required = false) Integer piso,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), TAMANO_MAXIMO_PAGINA), Sort.by(Sort.Direction.ASC, "numero"));
        return ResponseEntity.ok(celdaService.listar(criterio, estado, tipo, piso, activo, pageable));
    }

    @GetMapping("/disponibles")
    @Operation(summary = "Listar celdas asignables",
            description = "Solo celdas activas, en estado DISPONIBLE y sin registro activo asociado")
    public ResponseEntity<List<CeldaDTO>> listarDisponibles(
            @RequestParam(required = false) TipoVehiculo tipo) {
        return ResponseEntity.ok(celdaService.listarAsignables(tipo).stream()
                .map(celdaService::aDTO)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener celda por ID", description = "Retorna una celda específica")
    public ResponseEntity<CeldaDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(celdaService.aDTO(celdaService.obtenerPorId(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crear celda",
            description = "Alta de una celda del inventario; nace activa y disponible (solo administrador)")
    public ResponseEntity<CeldaDTO> crear(@Valid @RequestBody CeldaCrearDTO dto,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        Celda creada = celdaService.crear(dto, userDetails.getUsername());
        return ResponseEntity.status(201).body(celdaService.aDTO(creada));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Editar celda",
            description = "Actualiza el inventario; si la celda está ocupada solo admite observaciones")
    public ResponseEntity<CeldaDTO> actualizar(@PathVariable Long id,
                                               @Valid @RequestBody CeldaActualizarDTO dto,
                                               @AuthenticationPrincipal UserDetails userDetails) {
        Celda actualizada = celdaService.actualizar(id, dto, userDetails.getUsername());
        return ResponseEntity.ok(celdaService.aDTO(actualizada));
    }

    @PatchMapping("/{id}/activo")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Activar o desactivar celda",
            description = "Baja lógica: se rechaza si hay un vehículo adentro y conserva el estado operativo")
    public ResponseEntity<CeldaDTO> cambiarActivo(@PathVariable Long id,
                                                   @Valid @RequestBody CambiarActivoCeldaDTO dto,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        Celda celda = celdaService.cambiarActivo(id, dto.getActivo(), userDetails.getUsername());
        return ResponseEntity.ok(celdaService.aDTO(celda));
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Cambiar estado operativo de celda",
            description = "Solo DISPONIBLE ↔ MANTENIMIENTO sobre celda activa y libre; OCUPADA no es asignable")
    public ResponseEntity<CeldaDTO> cambiarEstado(@PathVariable Long id,
                                                   @Valid @RequestBody CambiarEstadoCeldaDTO dto,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        Celda celda = celdaService.cambiarEstado(id, dto.getEstado(), userDetails.getUsername());
        return ResponseEntity.ok(celdaService.aDTO(celda));
    }
}
