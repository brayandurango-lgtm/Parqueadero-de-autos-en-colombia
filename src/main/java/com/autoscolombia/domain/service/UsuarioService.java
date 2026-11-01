package com.autoscolombia.domain.service;

import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.infrastructure.exception.BusinessRuleException;
import com.autoscolombia.infrastructure.exception.ResourceNotFoundException;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
import com.autoscolombia.presentation.dto.UsuarioCrearDTO;
import com.autoscolombia.presentation.dto.UsuarioDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Servicio para gestión de las cuentas del personal (administradores y operadores).
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    /**
     * Mensaje del conflicto que protege la cobertura administrativa. No cita ids ni valores personales.
     */
    public static final String MENSAJE_ULTIMO_ADMINISTRADOR =
            "No se puede desactivar ni cambiar de rol al último administrador activo del sistema";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    /**
     * Crea una cuenta del personal con la contraseña inicial obligatoria que trae la solicitud.
     * La contraseña solo se usa para el hash: no se copia en la entidad ni en el registro de auditoría.
     */
    @Transactional
    public Usuario crear(UsuarioCrearDTO dto) {
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
                .claveHash(passwordEncoder.encode(dto.getClave()))
                .tipo(dto.getTipo())
                .activo(dto.getActivo() != null ? dto.getActivo() : true)
                .build();

        return usuarioRepository.save(usuario);
    }

    /**
     * Actualiza los datos de una cuenta existente. La contraseña no forma parte de esta operación:
     * el hash persistido se conserva intacto.
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

        // Verificar si el documento ya existe en otro usuario
        Optional<Usuario> existentePorDocumento = usuarioRepository.findByDocumento(dto.getDocumento());
        if (existentePorDocumento.isPresent() && !existentePorDocumento.get().getId().equals(id)) {
            throw new BusinessRuleException("Ya existe otro usuario con el documento: " + dto.getDocumento());
        }

        boolean activoNuevo = dto.getActivo() != null
                ? dto.getActivo()
                : Boolean.TRUE.equals(usuario.getActivo());
        verificarCoberturaDeAdministradores(usuario, dto.getTipo(), activoNuevo);

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
     * Activa o desactiva una cuenta. Es la única operación de estado y la comparten {@code PATCH},
     * {@code PUT} y {@code DELETE} para que ninguna ruta salte la protección del último administrador.
     * Repetir el estado ya vigente no reescribe la fila.
     */
    @Transactional
    public Usuario cambiarEstado(Long id, boolean activo) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        if (Boolean.TRUE.equals(usuario.getActivo()) == activo) {
            return usuario;
        }

        verificarCoberturaDeAdministradores(usuario, usuario.getTipo(), activo);

        usuario.setActivo(activo);
        Usuario guardado = usuarioRepository.save(usuario);
        auditoriaService.registrar(
                activo ? "USUARIO_ACTIVAR" : "USUARIO_DESACTIVAR",
                "Usuario",
                guardado.getId(),
                null,
                "EXITO",
                (activo ? "Activación" : "Desactivación") + " de la cuenta " + guardado.getCorreo(),
                resolverActorActual()
        );
        return guardado;
    }

    private Usuario resolverActorActual() {
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                return usuarioRepository.findByCorreo(auth.getName()).orElse(null);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Elimina lógicamente una cuenta: equivale a desactivarla y por tanto respeta la misma regla.
     */
    @Transactional
    public void eliminar(Long id) {
        cambiarEstado(id, false);
    }

    /**
     * Impide que una operación deje al sistema sin cobertura de administrador activo.
     *
     * <p>Se aplica solo cuando el cambio retira un administrador que estaba activo (desactivarlo o
     * cambiar su rol). Antes de decidir, bloquea en escritura el conjunto de administradores activos
     * para que dos operaciones simultáneas no puedan dejar cero cuentas.
     */
    private void verificarCoberturaDeAdministradores(Usuario usuario, TipoUsuario tipoNuevo, boolean activoNuevo) {
        boolean eraAdministradorActivo = TipoUsuario.ADMINISTRADOR.equals(usuario.getTipo())
                && Boolean.TRUE.equals(usuario.getActivo());
        boolean seguiraAdministradorActivo = TipoUsuario.ADMINISTRADOR.equals(tipoNuevo) && activoNuevo;

        if (!eraAdministradorActivo || seguiraAdministradorActivo) {
            return;
        }

        boolean existeOtroAdministradorActivo = usuarioRepository
                .findByTipoAndActivo(TipoUsuario.ADMINISTRADOR, true)
                .stream()
                .anyMatch(otro -> !otro.getId().equals(usuario.getId()));

        if (!existeOtroAdministradorActivo) {
            throw new BusinessRuleException(MENSAJE_ULTIMO_ADMINISTRADOR);
        }
    }

    /**
     * Obtiene todos los usuarios.
     */
    public List<UsuarioDTO> obtenerTodos() {
        return usuarioRepository.findAll().stream()
                .map(this::aDTO)
                .collect(Collectors.toList());
    }

    /**
     * Proyección de salida de una cuenta: nunca incluye la contraseña ni su hash.
     */
    public UsuarioDTO aDTO(Usuario usuario) {
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
}
