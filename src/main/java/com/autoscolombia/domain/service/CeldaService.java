package com.autoscolombia.domain.service;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.Celda;
import com.autoscolombia.infrastructure.exception.BusinessRuleException;
import com.autoscolombia.infrastructure.exception.ResourceNotFoundException;
import com.autoscolombia.infrastructure.repository.CeldaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Servicio para gestión de celdas de parqueadero.
 */
@Service
@RequiredArgsConstructor
public class CeldaService {

    private final CeldaRepository celdaRepository;

    /**
     * Asigna la primera celda disponible del tipo especificado.
     */
    @Transactional
    public Celda asignarCeldaDisponible(TipoVehiculo tipo) {
        Optional<Celda> celdaOpt = celdaRepository.findFirstByTipoVehiculoAndEstadoOrderByNumeroAsc(
                tipo, EstadoCelda.DISPONIBLE);

        if (celdaOpt.isEmpty()) {
            throw new BusinessRuleException("No hay celdas disponibles para vehículos tipo " + tipo);
        }

        Celda celda = celdaOpt.get();
        celda.setEstado(EstadoCelda.OCUPADA);
        return celdaRepository.save(celda);
    }

    /**
     * Libera una celda marcándola como disponible.
     */
    @Transactional
    public void liberarCelda(Long celdaId) {
        Celda celda = celdaRepository.findById(celdaId)
                .orElseThrow(() -> new ResourceNotFoundException("Celda no encontrada con ID: " + celdaId));

        celda.setEstado(EstadoCelda.DISPONIBLE);
        celdaRepository.save(celda);
    }

    /**
     * Cuenta las celdas por estado.
     */
    public Map<EstadoCelda, Long> contarCeldasPorEstado() {
        return Map.of(
                EstadoCelda.DISPONIBLE, celdaRepository.countByEstado(EstadoCelda.DISPONIBLE),
                EstadoCelda.OCUPADA, celdaRepository.countByEstado(EstadoCelda.OCUPADA),
                EstadoCelda.MANTENIMIENTO, celdaRepository.countByEstado(EstadoCelda.MANTENIMIENTO)
        );
    }

    /**
     * Obtiene todas las celdas.
     */
    public List<Celda> obtenerTodas() {
        return celdaRepository.findAll();
    }

    /**
     * Obtiene una celda por ID.
     */
    public Celda obtenerPorId(Long id) {
        return celdaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Celda no encontrada con ID: " + id));
    }

    /**
     * Cambia el estado de una celda.
     */
    @Transactional
    public Celda cambiarEstado(Long id, EstadoCelda nuevoEstado) {
        Celda celda = obtenerPorId(id);
        celda.setEstado(nuevoEstado);
        return celdaRepository.save(celda);
    }
}
