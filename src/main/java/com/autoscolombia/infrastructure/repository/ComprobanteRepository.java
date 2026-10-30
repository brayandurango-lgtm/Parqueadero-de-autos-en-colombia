package com.autoscolombia.infrastructure.repository;

import com.autoscolombia.domain.enums.TipoComprobante;
import com.autoscolombia.domain.model.Comprobante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio JPA para la entidad Comprobante.
 */
@Repository
public interface ComprobanteRepository extends JpaRepository<Comprobante, Long> {

    Optional<Comprobante> findByRegistroIdAndTipo(Long registroId, TipoComprobante tipo);
}
