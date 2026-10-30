package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.enums.TipoComprobante;
import com.autoscolombia.domain.model.Comprobante;
import com.autoscolombia.infrastructure.repository.ComprobanteRepository;
import com.autoscolombia.presentation.dto.ComprobanteDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controlador REST para gestión de comprobantes.
 */
@RestController
@RequestMapping("/api/v1/comprobantes")
@RequiredArgsConstructor
@Tag(name = "Comprobantes", description = "Operaciones de gestión de comprobantes")
public class ComprobanteController {

    private final ComprobanteRepository comprobanteRepository;

    @GetMapping("/{codigo}/pdf")
    @Operation(summary = "Obtener comprobante PDF", description = "Retorna un comprobante en formato PDF (simulado)")
    public ResponseEntity<String> obtenerComprobantePDF(@PathVariable String codigo) {
        // Nota: En una implementación real, aquí se generaría un PDF real
        // Por ahora retornamos el código QR en base64
        Comprobante comprobante = comprobanteRepository.findAll().stream()
                .filter(c -> c.getCodigo().equals(codigo))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Comprobante no encontrado"));

        return ResponseEntity.ok(comprobante.getQrBase64());
    }

    @GetMapping
    @Operation(summary = "Listar comprobantes", description = "Retorna comprobantes por registro")
    public ResponseEntity<List<ComprobanteDTO>> listarPorRegistro(@RequestParam Long registroId) {
        List<Comprobante> comprobantes = comprobanteRepository.findAll().stream()
                .filter(c -> c.getRegistro().getId().equals(registroId))
                .collect(Collectors.toList());

        List<ComprobanteDTO> dtos = comprobantes.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    private ComprobanteDTO toDTO(Comprobante comprobante) {
        return ComprobanteDTO.builder()
                .id(comprobante.getId())
                .codigo(comprobante.getCodigo())
                .tipo(comprobante.getTipo())
                .fechaGeneracion(comprobante.getFechaGeneracion())
                .qrBase64(comprobante.getQrBase64())
                .placa(comprobante.getRegistro().getVehiculo().getPlaca())
                .numeroCelda(comprobante.getRegistro().getCelda().getNumero())
                .build();
    }
}
