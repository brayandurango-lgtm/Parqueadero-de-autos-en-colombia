package com.autoscolombia.infrastructure.repository;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.Celda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

/**
 * Repositorio JPA para la entidad Celda.
 */
@Repository
public interface CeldaRepository extends JpaRepository<Celda, Long> {

    Optional<Celda> findFirstByTipoVehiculoAndEstadoOrderByNumeroAsc(
            TipoVehiculo tipo, EstadoCelda estado);

    long countByEstado(EstadoCelda estado);
}
