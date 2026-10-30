package com.autoscolombia.infrastructure.repository;

import com.autoscolombia.domain.model.Novedad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio JPA para la entidad Novedad.
 */
@Repository
public interface NovedadRepository extends JpaRepository<Novedad, Long> {

    List<Novedad> findByRegistroIdOrderByFechaHoraDesc(Long registroId);

    List<Novedad> findByFechaHoraBetweenOrderByFechaHoraDesc(LocalDateTime desde, LocalDateTime hasta);
}
