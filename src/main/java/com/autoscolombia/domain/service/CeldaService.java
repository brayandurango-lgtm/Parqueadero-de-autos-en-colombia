package com.autoscolombia.domain.service;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.EstadoRegistro;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.Celda;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.infrastructure.exception.BusinessRuleException;
import com.autoscolombia.infrastructure.exception.ResourceNotFoundException;
import com.autoscolombia.infrastructure.repository.CeldaRepository;
import com.autoscolombia.infrastructure.repository.RegistroRepository;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
import com.autoscolombia.presentation.dto.CeldaActualizarDTO;
import com.autoscolombia.presentation.dto.CeldaCrearDTO;
import com.autoscolombia.presentation.dto.CeldaDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio para gestión de celdas de parqueadero.
 *
 * <p>Dos cosas mandan sobre una celda: {@code estado} (operativo: DISPONIBLE, OCUPADA, MANTENIMIENTO) y
 * {@code activo} (baja lógica). {@code OCUPADA} no es editable: es consecuencia de una entrada válida. Las
 * escrituras pasan por {@link CeldaRepository#buscarConBloqueo(Long)}, de modo que dos operaciones
 * concurrentes sobre la misma celda se serializan y la que continúa revalida sobre la fila ya bloqueada.
 */
@Service
@RequiredArgsConstructor
public class CeldaService {

    public static final String MENSAJE_CODIGO_DUPLICADO = "Ya existe una celda con ese código";
    public static final String MENSAJE_OCUPADA_NO_ASIGNABLE_MANUAL =
            "El estado OCUPADA se produce al registrar una entrada, no se puede asignar manualmente";
    public static final String MENSAJE_TRANSICION_NO_PERMITIDA =
            "Solo se permite DISPONIBLE ↔ MANTENIMIENTO sobre una celda libre";
    public static final String MENSAJE_CELDA_INACTIVA =
            "La celda está inactiva; reactívala antes de cambiar su estado operativo";
    public static final String MENSAJE_CELDA_OCUPADA_NO_EDITABLE =
            "La celda está ocupada: solo se pueden cambiar las observaciones";
    public static final String MENSAJE_CELDA_OCUPADA_NO_DESACTIVABLE =
            "La celda tiene un vehículo adentro; no se puede desactivar";
    public static final String MENSAJE_INCONSISTENCIA =
            "El estado de la celda no coincide con sus registros activos";
    public static final String MENSAJE_CELDA_EN_OTRO_REGISTRO =
            "La celda corresponde a otro registro activo";

    /** Máximo de candidatos que se intentan por asignación: el reintento es acotado, no indefinido. */
    public static final int MAXIMO_CANDIDATOS_POR_ASIGNACION = 5;

    private final CeldaRepository celdaRepository;
    private final RegistroRepository registroRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    /**
     * Normaliza el código de la celda: sin espacios exteriores y en mayúsculas con {@link Locale#ROOT}, para
     * que la unicidad de {@code uk_celda_numero} compare siempre la misma forma del texto.
     */
    public static String normalizarCodigo(String numero) {
        return numero == null ? null : numero.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Alta de celda. Nace activa y libre: la ocupación solo llega con una entrada.
     */
    @Transactional
    public Celda crear(CeldaCrearDTO dto, String actorCorreo) {
        String codigo = normalizarCodigo(dto.getNumero());

        if (celdaRepository.findByNumero(codigo).isPresent()) {
            throw new BusinessRuleException(MENSAJE_CODIGO_DUPLICADO);
        }

        Celda celda = Celda.builder()
                .numero(codigo)
                .piso(dto.getPiso())
                .tipoVehiculo(dto.getTipoVehiculo())
                .observaciones(ClienteService.normalizarTexto(dto.getObservaciones()))
                .estado(EstadoCelda.DISPONIBLE)
                .activo(Boolean.TRUE)
                .build();

        Celda guardada = celdaRepository.save(celda);
        auditoriaService.registrar("CELDA_CREAR", "Celda", guardada.getId(), null, "EXITO",
                "Alta de celda " + guardada.getNumero(), resolverActor(actorCorreo));
        return guardada;
    }

    /**
     * Edición administrativa de los datos de la celda.
     *
     * <p>Si la celda está ocupada de verdad —estado OCUPADA o un registro ACTIVO encima— solo se admiten
     * observaciones; código, piso y tipo se rechazan para no romper la trazabilidad del vehículo que está
     * adentro. La escritura se hace sobre la fila bloqueada.
     */
    @Transactional
    public Celda actualizar(Long id, CeldaActualizarDTO dto, String actorCorreo) {
        Celda celda = obtenerConBloqueo(id);
        String codigo = normalizarCodigo(dto.getNumero());
        boolean ocupadaReal = estaOcupada(celda);

        exigirCoherencia(celda);

        if (ocupadaReal && cambiaIdentidadFisica(celda, codigo, dto)) {
            throw new BusinessRuleException(MENSAJE_CELDA_OCUPADA_NO_EDITABLE);
        }

        if (!codigo.equals(celda.getNumero())) {
            Optional<Celda> otra = celdaRepository.findByNumero(codigo);
            if (otra.isPresent() && !otra.get().getId().equals(id)) {
                throw new BusinessRuleException(MENSAJE_CODIGO_DUPLICADO);
            }
            celda.setNumero(codigo);
        }

        if (!ocupadaReal) {
            celda.setPiso(dto.getPiso());
            celda.setTipoVehiculo(dto.getTipoVehiculo());
        }
        celda.setObservaciones(ClienteService.normalizarTexto(dto.getObservaciones()));

        Celda guardada = celdaRepository.save(celda);
        auditoriaService.registrar("CELDA_EDITAR", "Celda", guardada.getId(), null, "EXITO",
                "Actualización de la celda " + guardada.getNumero(), resolverActor(actorCorreo));
        return guardada;
    }

    /**
     * Transición manual de estado operativo. Solo las dos transiciones autorizadas del encargo:
     * DISPONIBLE → MANTENIMIENTO y MANTENIMIENTO → DISPONIBLE, sobre una celda activa y sin ocupación.
     *
     * <p>No hay ruta para poner OCUPADA ni para liberar a mano: {@code OCUPADA} se produce en la entrada y se
     * revoca en la salida o la cancelación.
     */
    @Transactional
    public Celda cambiarEstado(Long id, EstadoCelda nuevoEstado, String actorCorreo) {
        if (nuevoEstado == EstadoCelda.OCUPADA) {
            throw new BusinessRuleException(MENSAJE_OCUPADA_NO_ASIGNABLE_MANUAL);
        }

        Celda celda = obtenerConBloqueo(id);

        if (!Boolean.TRUE.equals(celda.getActivo())) {
            throw new BusinessRuleException(MENSAJE_CELDA_INACTIVA);
        }

        exigirCoherencia(celda);

        if (celda.getEstado() == nuevoEstado) {
            return celda;
        }

        if (celda.getEstado() != EstadoCelda.DISPONIBLE && celda.getEstado() != EstadoCelda.MANTENIMIENTO) {
            throw new BusinessRuleException(MENSAJE_TRANSICION_NO_PERMITIDA);
        }

        celda.setEstado(nuevoEstado);
        Celda guardada = celdaRepository.save(celda);
        auditoriaService.registrar("CELDA_ESTADO", "Celda", guardada.getId(), null, "EXITO",
                (nuevoEstado == EstadoCelda.MANTENIMIENTO ? "Inicio" : "Fin") + " de mantenimiento de la celda "
                        + guardada.getNumero(), resolverActor(actorCorreo));
        return guardada;
    }

    /**
     * Alta y baja lógica de la celda.
     *
     * <p>Desactivar se rechaza si hay ocupación activa y se permite con el historial cerrado. La celda
     * conserva el estado operativo que tenía y reactivarla no le quita un mantenimiento pendiente.
     */
    @Transactional
    public Celda cambiarActivo(Long id, boolean activo, String actorCorreo) {
        Celda celda = obtenerConBloqueo(id);

        exigirCoherencia(celda);

        if (Boolean.TRUE.equals(celda.getActivo()) == activo) {
            return celda;
        }

        if (!activo && estaOcupada(celda)) {
            throw new BusinessRuleException(MENSAJE_CELDA_OCUPADA_NO_DESACTIVABLE);
        }

        celda.setActivo(activo);
        Celda guardada = celdaRepository.save(celda);
        auditoriaService.registrar(activo ? "CELDA_ACTIVAR" : "CELDA_DESACTIVAR", "Celda", guardada.getId(),
                null, "EXITO", (activo ? "Reactivación" : "Desactivación") + " de la celda "
                        + guardada.getNumero() + " (estado operativo " + guardada.getEstado() + ")",
                resolverActor(actorCorreo));
        return guardada;
    }

    /**
     * Asigna la primera celda assignable del tipo indicado y la deja OCUPADA.
     *
     * <p>Lee los candidatos sin bloqueo, pero antes de ocupar cada uno acquire la fila y revalida las tres
     * condiciones (activa, DISPONIBLE y sin registro ACTIVO). Si otro ingreso ocupó ese candidato entre la
     * lectura y el bloqueo, se pasa al siguiente candidato hasta un máximo acotado; nunca se reintenta
     * indefinidamente. El orden fijo {@code numero, id} hace que dos transacciones que compiten bloqueen en
     * el mismo orden y no se entorpen mutuamente.
     */
    @Transactional
    public Celda asignarCeldaDisponible(TipoVehiculo tipo) {
        List<Celda> candidatos = celdaRepository.listarAsignablesPorTipo(tipo);
        int intentos = Math.min(candidatos.size(), MAXIMO_CANDIDATOS_POR_ASIGNACION);

        for (int indice = 0; indice < intentos; indice++) {
            Celda bloqueada = obtenerConBloqueo(candidatos.get(indice).getId());

            if (!Boolean.TRUE.equals(bloqueada.getActivo())
                    || bloqueada.getEstado() != EstadoCelda.DISPONIBLE
                    || registroRepository.existsByCeldaIdAndEstado(bloqueada.getId(), EstadoRegistro.ACTIVO)) {
                continue;
            }

            bloqueada.setEstado(EstadoCelda.OCUPADA);
            return celdaRepository.save(bloqueada);
        }

        throw new BusinessRuleException("No hay celdas disponibles para vehículos tipo " + tipo);
    }

    /**
     * Libera la celda del registro que está cerrando o cancelando el llamador.
     *
     * <p>Se llama ya cerrado el propio registro, así que cualquier registro ACTIVO que quede sobre esa celda
     * pertenece a otro vehículo y liberarla sería incorrecto. Tampoco se repara el estado si no era OCUPADA:
     * la inconsistencia se rechaza para que se vea, no para que se silencie.
     */
    @Transactional
    public void liberarCelda(Long celdaId, Long registroPropioId) {
        Celda celda = obtenerConBloqueo(celdaId);

        if (registroRepository.existsByCeldaIdAndEstado(celdaId, EstadoRegistro.ACTIVO)) {
            throw new BusinessRuleException(MENSAJE_CELDA_EN_OTRO_REGISTRO);
        }

        if (celda.getEstado() != EstadoCelda.OCUPADA) {
            throw new BusinessRuleException(MENSAJE_INCONSISTENCIA);
        }

        celda.setEstado(EstadoCelda.DISPONIBLE);
        celdaRepository.save(celda);
    }

    /**
     * Cantidad de celdas por estado operativo tal como se guardan.
     */
    public Map<EstadoCelda, Long> contarCeldasPorEstado() {
        return Map.of(
                EstadoCelda.DISPONIBLE, celdaRepository.countByEstado(EstadoCelda.DISPONIBLE),
                EstadoCelda.OCUPADA, celdaRepository.countByEstado(EstadoCelda.OCUPADA),
                EstadoCelda.MANTENIMIENTO, celdaRepository.countByEstado(EstadoCelda.MANTENIMIENTO)
        );
    }

    /**
     * Todas las celdas, sin filtros. Se conserva porque el contrato de {@code GET /celdas} devuelve array.
     */
    public List<Celda> obtenerTodas() {
        return celdaRepository.findAll();
    }

    public Celda obtenerPorId(Long id) {
        return celdaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Celda no encontrada con ID: " + id));
    }

    /**
     * Lista paginada para la pantalla de administración, con los filtros por código, estado, tipo, piso y
     * estado lógico.
     */
    public Page<CeldaDTO> listar(String criterio, EstadoCelda estado, TipoVehiculo tipo, Integer piso,
                                 Boolean activo, Pageable pageable) {
        // Evita enlazar un String nulo en funciones SQL como upper(), que PostgreSQL puede tipar como bytea.
        String numero = criterio == null || criterio.trim().isEmpty() ? "" : criterio.trim();
        return celdaRepository.buscar(numero, estado, tipo, piso, activo, pageable).map(this::aDTO);
    }

    /**
     * Celdas realmente asignables: activas, DISPONIBLES y sin registro ACTIVO. Es la lista que consume la
     * operación y la única que el dashboard cuenta como disponible.
     */
    public List<Celda> listarAsignables(TipoVehiculo tipo) {
        return tipo == null
                ? celdaRepository.listarAsignables()
                : celdaRepository.listarAsignablesPorTipo(tipo);
    }

    public CeldaDTO aDTO(Celda celda) {
        return CeldaDTO.builder()
                .id(celda.getId())
                .numero(celda.getNumero())
                .piso(celda.getPiso())
                .tipoVehiculo(celda.getTipoVehiculo())
                .estado(celda.getEstado())
                .observaciones(celda.getObservaciones())
                .activo(celda.getActivo())
                .disponible(estaAsignable(celda))
                .build();
    }

    /**
     * Celda asignable: activa, en estado DISPONIBLE y sin un registro ACTIVO asociado.
     */
    public boolean estaAsignable(Celda celda) {
        return Boolean.TRUE.equals(celda.getActivo())
                && celda.getEstado() == EstadoCelda.DISPONIBLE
                && !registroRepository.existsByCeldaIdAndEstado(celda.getId(), EstadoRegistro.ACTIVO);
    }

    /**
     * Ocupación real: la declara el estado, la confirma un registro ACTIVO sobre la misma celda. Se aceptan
     * las dos señales porque la inconsistencia debe rechazarse, no resolverse eligiendo una.
     */
    private boolean estaOcupada(Celda celda) {
        return celda.getEstado() == EstadoCelda.OCUPADA
                || registroRepository.existsByCeldaIdAndEstado(celda.getId(), EstadoRegistro.ACTIVO);
    }

    private void exigirCoherencia(Celda celda) {
        boolean hayRegistroActivo = registroRepository.existsByCeldaIdAndEstado(celda.getId(),
                EstadoRegistro.ACTIVO);
        if (hayRegistroActivo && celda.getEstado() != EstadoCelda.OCUPADA) {
            throw new BusinessRuleException(MENSAJE_INCONSISTENCIA);
        }
        if (!hayRegistroActivo && celda.getEstado() == EstadoCelda.OCUPADA) {
            throw new BusinessRuleException(MENSAJE_INCONSISTENCIA);
        }
    }

    private boolean cambiaIdentidadFisica(Celda celda, String codigo, CeldaActualizarDTO dto) {
        return !codigo.equals(celda.getNumero())
                || !dto.getPiso().equals(celda.getPiso())
                || dto.getTipoVehiculo() != celda.getTipoVehiculo();
    }

    private Celda obtenerConBloqueo(Long id) {
        return celdaRepository.buscarConBloqueo(id)
                .orElseThrow(() -> new ResourceNotFoundException("Celda no encontrada con ID: " + id));
    }

    private Usuario resolverActor(String correo) {
        return usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new ResourceNotFoundException("Operador no encontrado"));
    }
}
