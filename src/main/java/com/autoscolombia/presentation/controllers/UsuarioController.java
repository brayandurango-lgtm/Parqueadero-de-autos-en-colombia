package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.domain.service.UsuarioService;
import com.autoscolombia.presentation.dto.UsuarioDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para gestión de usuarios (solo administradores).
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Tag(name = "Usuarios", description = "Operaciones de gestión de usuarios (solo administrador)")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    @Operation(summary = "Listar usuarios", description = "Retorna todos los usuarios del sistema")
    public ResponseEntity<List<UsuarioDTO>> listar() {
        return ResponseEntity.ok(usuarioService.obtenerTodos());
    }

    @PostMapping
    @Operation(summary = "Crear usuario", description = "Crea un nuevo usuario")
    public ResponseEntity<UsuarioDTO> crear(@Valid @RequestBody UsuarioDTO dto) {
        // Nota: En una implementación real, la clave debería venir encriptada o de forma segura
        // Aquí usamos una clave por defecto para simplificar
        Usuario usuario = usuarioService.crear(dto, "clave123");
        dto.setId(usuario.getId());
        return ResponseEntity.status(201).body(dto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar usuario", description = "Actualiza un usuario existente")
    public ResponseEntity<UsuarioDTO> actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioDTO dto) {
        Usuario usuario = usuarioService.actualizar(id, dto);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar usuario", description = "Elimina lógicamente un usuario (desactiva)")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        usuarioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
