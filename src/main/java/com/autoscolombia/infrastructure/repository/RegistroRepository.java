package com.autoscolombia.infrastructure.repository;

import com.autoscolombia.domain.enums.EstadoRegistro;
import com.autoscolombia.domain.model.Registro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para la entidad Registro.
 */
@Repository
public interface RegistroRepository extends JpaRepository<Registro, Long> {

    Optional<Registro> findByVehiculoPlacaAndEstado(String placa, EstadoRegistro estado);

    List<Registro> findByEstadoOrderByFechaHoraEntradaDesc(EstadoRegistro estado);

    List<Registro> findByFechaHoraEntradaBetween(LocalDateTime desde, LocalDateTime hasta);

    long countByFechaHoraEntradaBetween(LocalDateTime desde, LocalDateTime hasta);
}
