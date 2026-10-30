package com.autoscolombia.domain.service;

import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.infrastructure.exception.BusinessRuleException;
import com.autoscolombia.infrastructure.exception.ResourceNotFoundException;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
import com.autoscolombia.presentation.dto.UsuarioDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Servicio para gestión de usuarios.
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Crea un nuevo usuario.
     */
    @Transactional
    public Usuario crear(UsuarioDTO dto, String clave) {
        // Verificar si el correo ya existe
        if (usuarioRepository.findByCorreo(dto.getCorreo()).isPresent()) {
            throw new BusinessRuleException("Ya existe un usuario con el correo: " + dto.getCorreo());
        }

        // Verificar si el documento ya existe
        if (usuarioRepository.findAll().stream().anyMatch(u -> u.getDocumento().equals(dto.getDocumento()))) {
            throw new BusinessRuleException("Ya existe un usuario con el documento: " + dto.getDocumento());
        }

        Usuario usuario = Usuario.builder()
                .nombre(dto.getNombre())
                .apellido(dto.getApellido())
                .documento(dto.getDocumento())
                .correo(dto.getCorreo())
                .telefono(dto.getTelefono())
                .claveHash(passwordEncoder.encode(clave))
                .tipo(dto.getTipo())
                .activo(dto.getActivo() != null ? dto.getActivo() : true)
                .build();

        return usuarioRepository.save(usuario);
    }

    /**
     * Actualiza un usuario existente.
     */
    @Transactional
    public Usuario actualizar(Long id, UsuarioDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        // Verificar si el correo ya existe en otro usuario
        Optional<Usuario> existente = usuarioRepository.findByCorreo(dto.getCorreo());
        if (existente.isPresent() && !existente.get().getId().equals(id)) {
            throw new BusinessRuleException("Ya existe otro usuario con el correo: " + dto.getCorreo());
        }

        usuario.setNombre(dto.getNombre());
        usuario.setApellido(dto.getApellido());
        usuario.setDocumento(dto.getDocumento());
        usuario.setCorreo(dto.getCorreo());
        usuario.setTelefono(dto.getTelefono());
        usuario.setTipo(dto.getTipo());
        if (dto.getActivo() != null) {
            usuario.setActivo(dto.getActivo());
        }

        return usuarioRepository.save(usuario);
    }

    /**
     * Elimina lógicamente un usuario (desactiva).
     */
    @Transactional
    public void eliminar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }

    /**
     * Obtiene todos los usuarios.
     */
    public List<UsuarioDTO> obtenerTodos() {
        return usuarioRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene un usuario por ID.
     */
    public Usuario obtenerPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
    }

    /**
     * Obtiene un usuario por correo.
     */
    public Optional<Usuario> obtenerPorCorreo(String correo) {
        return usuarioRepository.findByCorreo(correo);
    }

    private UsuarioDTO toDTO(Usuario usuario) {
        return UsuarioDTO.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .apellido(usuario.getApellido())
                .documento(usuario.getDocumento())
                .correo(usuario.getCorreo())
                .telefono(usuario.getTelefono())
                .tipo(usuario.getTipo())
                .activo(usuario.getActivo())
                .build();
    }
}
