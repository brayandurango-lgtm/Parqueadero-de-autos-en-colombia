package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.Vehiculo;
import com.autoscolombia.presentation.dto.VehiculoDTO;
import com.autoscolombia.presentation.dto.VincularClienteDTO;
import com.autoscolombia.domain.service.VehiculoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para gestión de vehículos.
 */
@RestController
@RequestMapping("/api/v1/vehiculos")
@RequiredArgsConstructor
@Tag(name = "Vehículos", description = "Operaciones de gestión de vehículos")
public class VehiculoController {

    private final VehiculoService vehiculoService;

    @GetMapping
    @Operation(summary = "Buscar vehículos", description = "Busca vehículos por criterio flexible")
    public ResponseEntity<List<VehiculoDTO>> buscar(
            @RequestParam(required = false) String criterio,
            @RequestParam(required = false) TipoVehiculo tipo,
            @RequestParam(required = false) String estado) {
        // Nota: el estado se ignora en esta versión simple porque está en Registro, no en Vehiculo
        List<VehiculoDTO> vehiculos = vehiculoService.buscar(criterio, tipo, null);
        return ResponseEntity.ok(vehiculos);
    }

    @GetMapping("/{placa}")
    @Operation(summary = "Obtener vehículo por placa", description = "Retorna un vehículo específico por su placa")
    public ResponseEntity<VehiculoDTO> obtenerPorPlaca(@PathVariable String placa) {
        return vehiculoService.buscarPorPlaca(placa)
                .map(vehiculo -> ResponseEntity.ok(vehiculoService.buscar(placa, null, null).get(0)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Crear vehículo", description = "Crea un nuevo vehículo en el sistema")
    public ResponseEntity<VehiculoDTO> crear(@Valid @RequestBody VehiculoDTO dto) {
        Vehiculo creado = vehiculoService.crearOActualizar(dto);
        return ResponseEntity.status(201).body(vehiculoService.toDTO(creado));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar vehículo", description = "Actualiza un vehículo existente")
    public ResponseEntity<VehiculoDTO> actualizar(@PathVariable Long id, @Valid @RequestBody VehiculoDTO dto) {
        Vehiculo guardado = vehiculoService.crearOActualizar(dto);
        return ResponseEntity.ok(vehiculoService.toDTO(guardado));
    }

    /**
     * Ruta elegida para la relación: es la única operación de vinculación y vive junto al vehículo al que
     * modifica, con la placa como llave natural (el mismo criterio de {@code GET /{placa}} y de
     * {@code PUT /registros/salida/{placa}}). No existe un endpoint equivalente en el controlador de clientes.
     */
    @PutMapping("/{placa}/cliente")
    @Operation(summary = "Vincular o cambiar el cliente titular",
            description = "Vincula un cliente activo. Cambiar un titular ya vinculado exige ADMINISTRADOR y "
                    + "que el vehículo no tenga un ingreso activo")
    public ResponseEntity<VehiculoDTO> vincularCliente(@PathVariable String placa,
                                                      @Valid @RequestBody VincularClienteDTO dto,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        Vehiculo vehiculo = vehiculoService.vincularCliente(placa, dto.getClienteId(), userDetails.getUsername());
        return ResponseEntity.ok(vehiculoService.toDTO(vehiculo));
    }
}
