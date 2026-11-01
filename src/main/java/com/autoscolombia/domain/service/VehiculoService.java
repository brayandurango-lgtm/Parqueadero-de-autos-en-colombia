package com.autoscolombia.domain.service;

import com.autoscolombia.domain.enums.EstadoRegistro;
import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.Cliente;
import com.autoscolombia.domain.model.Usuario;
import com.autoscolombia.domain.model.Vehiculo;
import com.autoscolombia.infrastructure.exception.BusinessRuleException;
import com.autoscolombia.infrastructure.exception.ResourceNotFoundException;
import com.autoscolombia.infrastructure.repository.ClienteRepository;
import com.autoscolombia.infrastructure.repository.RegistroRepository;
import com.autoscolombia.infrastructure.repository.UsuarioRepository;
import com.autoscolombia.infrastructure.repository.VehiculoRepository;
import com.autoscolombia.presentation.dto.VehiculoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Servicio para gestión de vehículos y de su vínculo con el cliente titular.
 */
@Service
@RequiredArgsConstructor
public class VehiculoService {

    private final VehiculoRepository vehiculoRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final RegistroRepository registroRepository;
    private final AuditoriaService auditoriaService;

    /**
     * Crea o actualiza un vehículo.
     *
     * <p>Cuando el vehículo ya tiene un cliente vinculado, los campos {@code propietario_*} de la solicitud
     * <strong>no</strong> se escriben: la titularidad la define el cliente y el texto libre no puede
     * cambiarla de forma silenciosa. La proyección a DTO ({@link #toDTO}) lee siempre del cliente en ese caso.
     */
    @Transactional
    public Vehiculo crearOActualizar(VehiculoDTO vehiculoDTO) {
        Optional<Vehiculo> existente = vehiculoRepository.findByPlaca(vehiculoDTO.getPlaca());

        if (existente.isPresent()) {
            Vehiculo vehiculo = existente.get();
            vehiculo.setMarca(vehiculoDTO.getMarca());
            vehiculo.setModelo(vehiculoDTO.getModelo());
            vehiculo.setColor(vehiculoDTO.getColor());
            vehiculo.setTipo(vehiculoDTO.getTipo());
            if (vehiculo.getCliente() == null) {
                vehiculo.setPropietarioDocumento(vehiculoDTO.getPropietarioDocumento());
                vehiculo.setPropietarioNombre(vehiculoDTO.getPropietarioNombre());
                vehiculo.setPropietarioTelefono(vehiculoDTO.getPropietarioTelefono());
            }
            return vehiculoRepository.save(vehiculo);
        } else {
            Vehiculo vehiculo = Vehiculo.builder()
                    .placa(vehiculoDTO.getPlaca())
                    .marca(vehiculoDTO.getMarca())
                    .modelo(vehiculoDTO.getModelo())
                    .color(vehiculoDTO.getColor())
                    .tipo(vehiculoDTO.getTipo())
                    .propietarioDocumento(vehiculoDTO.getPropietarioDocumento())
                    .propietarioNombre(vehiculoDTO.getPropietarioNombre())
                    .propietarioTelefono(vehiculoDTO.getPropietarioTelefono())
                    .build();
            return vehiculoRepository.save(vehiculo);
        }
    }

    /**
     * Busca un vehículo por placa.
     */
    public Optional<Vehiculo> buscarPorPlaca(String placa) {
        return vehiculoRepository.findByPlaca(placa);
    }

    /**
     * Búsqueda flexible de vehículos.
     */
    public List<VehiculoDTO> buscar(String criterio, TipoVehiculo tipo, EstadoRegistro estado) {
        List<Vehiculo> vehiculos;

        if (criterio == null || criterio.trim().isEmpty()) {
            vehiculos = vehiculoRepository.findAll();
        } else {
            // El criterio puede referirse al texto histórico de propietario_* o al cliente ya vinculado.
            Map<Long, Vehiculo> sinDuplicados = new LinkedHashMap<>();
            vehiculoRepository
                    .findByPlacaContainingIgnoreCaseOrPropietarioNombreContainingIgnoreCaseOrPropietarioDocumentoContaining(
                            criterio, criterio, criterio)
                    .forEach(v -> sinDuplicados.put(v.getId(), v));
            vehiculoRepository
                    .findByClienteDocumentoContainingIgnoreCaseOrClienteNombreCompletoContainingIgnoreCase(
                            criterio, criterio)
                    .forEach(v -> sinDuplicados.put(v.getId(), v));
            vehiculos = new ArrayList<>(sinDuplicados.values());
        }

        // Filtrar por tipo si se especifica
        if (tipo != null) {
            vehiculos = vehiculos.stream()
                    .filter(v -> v.getTipo() == tipo)
                    .collect(Collectors.toList());
        }

        // Nota: El filtro por estado no se aplica directamente aquí porque
        // el estado está en la entidad Registro, no en Vehiculo.
        // Este filtro se puede implementar en el servicio de Registro.

        return vehiculos.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Vehículos cuyo titular actual es el cliente indicado.
     */
    public List<VehiculoDTO> listarVehiculosDeCliente(Long clienteId) {
        return vehiculoRepository.findByClienteId(clienteId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Vincula un cliente a un vehículo que todavía no tiene titular registrado.
     *
     * <p>Reglas aplicadas aquí y no en la interfaz: el cliente debe existir y estar activo, y la operación
     * queda auditada con el actor que la ejecutó.
     */
    @Transactional
    public Vehiculo vincularCliente(String placa, Long clienteId, String operadorCorreo) {
        Vehiculo vehiculo = vehiculoRepository.findByPlaca(placa)
                .orElseThrow(() -> new ResourceNotFoundException("Vehículo no encontrado con placa: " + placa));

        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + clienteId));

        if (!Boolean.TRUE.equals(cliente.getActivo())) {
            throw new BusinessRuleException("No se puede vincular un vehículo a un cliente inactivo");
        }

        Usuario actor = usuarioRepository.findByCorreo(operadorCorreo)
                .orElseThrow(() -> new ResourceNotFoundException("Operador no encontrado"));

        Cliente anterior = vehiculo.getCliente();
        if (anterior != null && anterior.getId().equals(cliente.getId())) {
            // El vehículo ya apunta a este cliente: no se guarda ni se audita un cambio que no ocurrió.
            return vehiculo;
        }

        if (anterior != null) {
            if (actor.getTipo() != TipoUsuario.ADMINISTRADOR) {
                throw new AccessDeniedException(
                        "Solo un administrador puede cambiar el cliente titular de un vehículo ya vinculado");
            }
            if (registroRepository.findByVehiculoPlacaAndEstado(placa, EstadoRegistro.ACTIVO).isPresent()) {
                throw new BusinessRuleException(
                        "El vehículo tiene un ingreso activo; registre la salida antes de cambiar su titular");
            }
            vehiculo.setCliente(cliente);
            Vehiculo guardado = vehiculoRepository.save(vehiculo);
            auditoriaService.registrar("VEHICULO_CAMBIAR_CLIENTE", "Vehiculo", guardado.getId(), null, "EXITO",
                    "Cambio de titular en placa " + guardado.getPlaca() + ": cliente " + anterior.getId()
                            + " sustituido por " + cliente.getId(), actor);
            return guardado;
        }

        vehiculo.setCliente(cliente);
        Vehiculo guardado = vehiculoRepository.save(vehiculo);
        auditoriaService.registrar("VEHICULO_VINCULAR_CLIENTE", "Vehiculo", guardado.getId(), null, "EXITO",
                "Vinculación de titular en placa " + guardado.getPlaca() + " al cliente " + cliente.getId(), actor);
        return guardado;
    }

    /**
     * Proyección única de vehículo a DTO.
     *
     * <p>Es el punto donde se decide la fuente de los datos del titular: si hay cliente vinculado, los campos
     * {@code propietario_*} se responden desde el cliente (fuente actual) y las columnas viejas quedan como
     * residuo histórico; si no lo hay, se responden las columnas tal cual, que es el comportamiento de la
     * Iteración 1. Ningún otro método duplica esta regla.
     */
    public VehiculoDTO toDTO(Vehiculo vehiculo) {
        Cliente cliente = vehiculo.getCliente();
        boolean vinculado = cliente != null;
        return VehiculoDTO.builder()
                .id(vehiculo.getId())
                .placa(vehiculo.getPlaca())
                .marca(vehiculo.getMarca())
                .modelo(vehiculo.getModelo())
                .color(vehiculo.getColor())
                .tipo(vehiculo.getTipo())
                .propietarioDocumento(vinculado ? cliente.getDocumento() : vehiculo.getPropietarioDocumento())
                .propietarioNombre(vinculado ? cliente.getNombreCompleto() : vehiculo.getPropietarioNombre())
                .propietarioTelefono(vinculado ? cliente.getTelefono() : vehiculo.getPropietarioTelefono())
                .clienteId(vinculado ? cliente.getId() : null)
                .build();
    }
}
