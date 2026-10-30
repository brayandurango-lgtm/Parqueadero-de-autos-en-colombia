package com.autoscolombia.infrastructure.repository;

import com.autoscolombia.domain.model.AuditoriaLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para la entidad AuditoriaLog.
 */
@Repository
public interface AuditoriaLogRepository extends JpaRepository<AuditoriaLog, Long> {
}
