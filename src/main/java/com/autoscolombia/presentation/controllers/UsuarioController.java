package com.autoscolombia.presentation.controllers;

import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.domain.service.UsuarioService;
import com.autoscolombia.presentation.dto.CambiarEstadoUsuarioDTO;
import com.autoscolombia.presentation.dto.UsuarioCrearDTO;
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
 * Controlador REST para gestión de las cuentas del personal (solo administradores).
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
    @Operation(summary = "Crear usuario",
            description = "Crea una cuenta del personal con la contraseña inicial obligatoria que envía la solicitud")
    public ResponseEntity<UsuarioDTO> crear(@Valid @RequestBody UsuarioCrearDTO dto) {
        Usuario creado = usuarioService.crear(dto);
        return ResponseEntity.status(201).body(usuarioService.aDTO(creado));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar usuario", description = "Actualiza un usuario existente sin tocar su contraseña")
    public ResponseEntity<UsuarioDTO> actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioDTO dto) {
        Usuario usuario = usuarioService.actualizar(id, dto);
        return ResponseEntity.ok(usuarioService.aDTO(usuario));
    }

    @PatchMapping("/{id}/activo")
    @Operation(summary = "Activar o desactivar usuario",
            description = "Cambia el estado de una cuenta. Rechaza la desactivación del último administrador activo")
    public ResponseEntity<UsuarioDTO> cambiarEstado(@PathVariable Long id,
                                                   @Valid @RequestBody CambiarEstadoUsuarioDTO dto) {
        Usuario usuario = usuarioService.cambiarEstado(id, dto.getActivo());
        return ResponseEntity.ok(usuarioService.aDTO(usuario));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar usuario", description = "Elimina lógicamente un usuario (desactiva)")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        usuarioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
