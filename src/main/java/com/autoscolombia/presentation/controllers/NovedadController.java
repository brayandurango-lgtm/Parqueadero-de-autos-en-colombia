package com.autoscolombia.presentation.controllers;

import com.autoscolombia.presentation.dto.NovedadDTO;
import com.autoscolombia.domain.service.NovedadService;
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
 * Controlador REST para gestión de novedades.
 */
@RestController
@RequestMapping("/api/v1/novedades")
@RequiredArgsConstructor
@Tag(name = "Novedades", description = "Operaciones de gestión de novedades")
public class NovedadController {

    private final NovedadService novedadService;

    @PostMapping
    @Operation(summary = "Registrar novedad", description = "Registra una novedad en un registro activo")
    public ResponseEntity<NovedadDTO> registrar(
            @Valid @RequestBody NovedadDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        novedadService.registrar(dto, userDetails.getUsername());
        return ResponseEntity.status(201).body(dto);
    }

    @GetMapping
    @Operation(summary = "Listar novedades", description = "Lista novedades con filtros opcionales")
    public ResponseEntity<List<NovedadDTO>> listar(
            @RequestParam(required = false) Long registroId,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        if (registroId != null) {
            return ResponseEntity.ok(novedadService.listarPorRegistro(registroId));
        }
        return ResponseEntity.ok(novedadService.listarPendientes());
    }

    @GetMapping("/pendientes")
    @Operation(summary = "Novedades pendientes", description = "Lista novedades de registros aún activos")
    public ResponseEntity<List<NovedadDTO>> listarPendientes() {
        return ResponseEntity.ok(novedadService.listarPendientes());
    }
}
