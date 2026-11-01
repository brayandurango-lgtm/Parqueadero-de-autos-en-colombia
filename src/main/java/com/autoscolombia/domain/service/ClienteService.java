package com.autoscolombia.domain.service;

import com.autoscolombia.domain.enums.TipoDocumento;
import com.autoscolombia.domain.model.Cliente;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.infrastructure.exception.BusinessRuleException;
import com.autoscolombia.infrastructure.exception.ResourceNotFoundException;
import com.autoscolombia.infrastructure.repository.ClienteRepository;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
import com.autoscolombia.presentation.dto.ClienteCrearDTO;
import com.autoscolombia.presentation.dto.ClienteDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Servicio para la gestión de clientes del parqueadero.
 *
 * <p>El cliente no es una cuenta de acceso: no tiene contraseña, ni rol, ni token. Las reglas de estado
 * ({@code activo}) se aplican sobre las vinculaciones y los ingresos, no sobre la autenticación.
 */
@Service
@RequiredArgsConstructor
public class ClienteService {

    /** Regla de ingreso: un cliente desactivado no admite un ingreso nuevo de sus vehículos. */
    public static final String MENSAJE_CLIENTE_INACTIVO =
            "El cliente vinculado está inactivo y no admite un nuevo ingreso";

    /** Regla de unicidad: la identidad del cliente es la pareja tipo de documento + documento. */
    public static final String MENSAJE_DOCUMENTO_DUPLICADO =
            "Ya existe un cliente registrado con ese tipo y número de documento";

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    /**
     * Normaliza el documento para comparar y guardar: quita espacios exteriores, pasa las letras a mayúsculas
     * con {@link Locale#ROOT} y conserva los ceros iniciales porque el resultado sigue siendo texto.
     */
    public static String normalizarDocumento(String documento) {
        return documento == null ? null : documento.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Normaliza los demás campos de texto: solo quita espacios exteriores, sin cambiar mayúsculas ni signos.
     */
    public static String normalizarTexto(String texto) {
        return texto == null ? null : texto.trim();
    }

    /**
     * Un cliente inactivo no admite nuevos ingresos de los vehículos que lo tienen como titular.
     * Un vehículo sin cliente vinculado siempre se admite: es el caso de la Iteración 1.
     */
    public static boolean permiteNuevoIngreso(Cliente cliente) {
        return cliente == null || Boolean.TRUE.equals(cliente.getActivo());
    }

    /**
     * Crea un cliente. El documento se normaliza antes de cualquier comparación o escritura.
     */
    @Transactional
    public Cliente crear(ClienteCrearDTO dto, String operadorCorreo) {
        TipoDocumento tipo = dto.getTipoDocumento();
        String documento = normalizarDocumento(dto.getDocumento());

        if (clienteRepository.findByTipoDocumentoAndDocumento(tipo, documento).isPresent()) {
            throw new BusinessRuleException(MENSAJE_DOCUMENTO_DUPLICADO);
        }

        Cliente cliente = Cliente.builder()
                .tipoDocumento(tipo)
                .documento(documento)
                .nombreCompleto(normalizarTexto(dto.getNombreCompleto()))
                .telefono(normalizarTexto(dto.getTelefono()))
                .activo(true)
                .build();

        Cliente guardado = clienteRepository.save(cliente);
        auditoriaService.registrar("CLIENTE_CREAR", "Cliente", guardado.getId(), null, "EXITO",
                "Alta de cliente id " + guardado.getId(), resolverActor(operadorCorreo));
        return guardado;
    }

    /**
     * Actualiza los datos de un cliente sin tocar su estado.
     *
     * <p>Permite corregir el documento o el tipo, pero rechaza quedarse con la identidad de otro cliente.
     */
    @Transactional
    public Cliente actualizar(Long id, ClienteCrearDTO dto, String operadorCorreo) {
        Cliente cliente = obtenerPorId(id);
        TipoDocumento tipo = dto.getTipoDocumento();
        String documento = normalizarDocumento(dto.getDocumento());

        Optional<Cliente> dueño = clienteRepository.findByTipoDocumentoAndDocumento(tipo, documento);
        if (dueño.isPresent() && !dueño.get().getId().equals(id)) {
            throw new BusinessRuleException(MENSAJE_DOCUMENTO_DUPLICADO);
        }

        cliente.setTipoDocumento(tipo);
        cliente.setDocumento(documento);
        cliente.setNombreCompleto(normalizarTexto(dto.getNombreCompleto()));
        cliente.setTelefono(normalizarTexto(dto.getTelefono()));

        Cliente guardado = clienteRepository.save(cliente);
        auditoriaService.registrar("CLIENTE_ACTUALIZAR", "Cliente", guardado.getId(), null, "EXITO",
                "Actualización de datos del cliente id " + guardado.getId(), resolverActor(operadorCorreo));
        return guardado;
    }

    /**
     * Activa o desactiva un cliente (baja lógica).
     *
     * <p>No borra nada: los vehículos vinculados y los registros históricos siguen existiendo. Si el estado
     * pedido ya es el vigente, la operación es idempotente y no escribe ni audita.
     */
    @Transactional
    public Cliente cambiarEstado(Long id, boolean activo, String operadorCorreo) {
        Cliente cliente = obtenerPorId(id);

        if (Boolean.TRUE.equals(cliente.getActivo()) == activo) {
            return cliente;
        }

        cliente.setActivo(activo);
        Cliente guardado = clienteRepository.save(cliente);
        auditoriaService.registrar("CLIENTE_CAMBIAR_ESTADO", "Cliente", guardado.getId(), null, "EXITO",
                (activo ? "Activación" : "Desactivación") + " del cliente id " + guardado.getId(),
                resolverActor(operadorCorreo));
        return guardado;
    }

    /**
     * Consulta un cliente por su ID.
     */
    public Cliente obtenerPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + id));
    }

    /**
     * Lista con paginación, filtro por estado y búsqueda por documento o nombre.
     *
     * <p>La búsqueda es por contención y sin distinguir mayúsculas, para que el operador escriba el documento
     * como le resulte natural aunque esté guardado normalizado.
     */
    public Page<ClienteDTO> listar(Boolean activo, String criterio, Pageable pageable) {
        Page<Cliente> pagina;
        String textoLimpio = criterio == null ? null : criterio.trim();

        if (textoLimpio == null || textoLimpio.isEmpty()) {
            pagina = activo == null
                    ? clienteRepository.findAll(pageable)
                    : clienteRepository.findByActivo(activo, pageable);
        } else if (activo == null) {
            pagina = clienteRepository.findByDocumentoContainingIgnoreCaseOrNombreCompletoContainingIgnoreCase(
                    textoLimpio, textoLimpio, pageable);
        } else {
            pagina = clienteRepository
                    .findByActivoAndDocumentoContainingIgnoreCaseOrActivoAndNombreCompletoContainingIgnoreCase(
                            activo, textoLimpio, activo, textoLimpio, pageable);
        }

        return pagina.map(this::aDTO);
    }

    /**
     * Proyección de la entidad al DTO de respuesta.
     */
    public ClienteDTO aDTO(Cliente cliente) {
        return ClienteDTO.builder()
                .id(cliente.getId())
                .tipoDocumento(cliente.getTipoDocumento())
                .documento(cliente.getDocumento())
                .nombreCompleto(cliente.getNombreCompleto())
                .telefono(cliente.getTelefono())
                .activo(cliente.getActivo())
                .fechaRegistro(cliente.getFechaRegistro())
                .build();
    }

    private Usuario resolverActor(String correo) {
        return usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new ResourceNotFoundException("Operador no encontrado"));
    }
}
