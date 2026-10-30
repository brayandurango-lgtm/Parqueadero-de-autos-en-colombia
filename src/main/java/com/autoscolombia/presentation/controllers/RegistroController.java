package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.model.Registro;
import com.autoscolombia.domain.service.RegistroService;
import com.autoscolombia.presentation.dto.RegistroDTO;
import com.autoscolombia.presentation.dto.RegistroEntradaDTO;
import com.autoscolombia.presentation.dto.RegistroSalidaDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST para gestión de registros de entrada y salida.
 */
@RestController
@RequestMapping("/api/v1/registros")
@RequiredArgsConstructor
@Tag(name = "Registros", description = "Operaciones de entrada y salida de vehículos")
public class RegistroController {

    private final RegistroService registroService;

    @PostMapping("/entrada")
    @Operation(summary = "Registrar entrada", description = "Registra la entrada de un vehículo al parqueadero")
    public ResponseEntity<RegistroDTO> registrarEntrada(
            @Valid @RequestBody RegistroEntradaDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        Registro registro = registroService.registrarEntrada(dto, userDetails.getUsername());
        return ResponseEntity.status(201).body(registroService.toDTO(registro));
    }

    @PutMapping("/salida/{placa}")
    @Operation(summary = "Registrar salida", description = "Registra la salida de un vehículo del parqueadero")
    public ResponseEntity<RegistroDTO> registrarSalida(
            @PathVariable String placa,
            @Valid @RequestBody RegistroSalidaDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        Registro registro = registroService.registrarSalida(placa, dto, userDetails.getUsername());
        return ResponseEntity.ok(registroService.toDTO(registro));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancelar registro", description = "Cancela un registro activo")
    public ResponseEntity<RegistroDTO> cancelarRegistro(
            @PathVariable Long id,
            @RequestBody Map<String, String> motivo,
            @AuthenticationPrincipal UserDetails userDetails) {
        Registro registro = registroService.cancelarRegistro(id, motivo.get("motivo"), userDetails.getUsername());
        return ResponseEntity.ok(registroService.toDTO(registro));
    }

    @GetMapping("/activos")
    @Operation(summary = "Listar registros activos", description = "Retorna todos los registros en estado ACTIVO")
    public ResponseEntity<List<RegistroDTO>> listarActivos() {
        List<Registro> registros = registroService.listarUltimosIngresos(100);
        List<RegistroDTO> dtos = registros.stream()
                .map(registroService::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{placa}")
    @Operation(summary = "Obtener registro por placa", description = "Retorna el registro activo de una placa")
    public ResponseEntity<RegistroDTO> obtenerPorPlaca(@PathVariable String placa) {
        return registroService.consultarActivoPorPlaca(placa)
                .map(registro -> ResponseEntity.ok(registroService.toDTO(registro)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/ultimos")
    @Operation(summary = "Últimos ingresos", description = "Retorna los últimos ingresos al parqueadero")
    public ResponseEntity<List<RegistroDTO>> listarUltimosIngresos(
            @RequestParam(defaultValue = "10") int limite) {
        List<Registro> registros = registroService.listarUltimosIngresos(limite);
        List<RegistroDTO> dtos = registros.stream()
                .map(registroService::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }
}
