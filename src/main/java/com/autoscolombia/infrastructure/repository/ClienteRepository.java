package com.autoscolombia.infrastructure.repository;

import com.autoscolombia.domain.enums.TipoDocumento;
import com.autoscolombia.domain.model.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio JPA para los clientes del parqueadero.
 */
@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    /**
     * Localiza al cliente por la identidad completa: tipo de documento + número.
     * La unicidad del negocio se define sobre el par, no sobre el número suelto.
     */
    Optional<Cliente> findByTipoDocumentoAndDocumento(TipoDocumento tipoDocumento, String documento);

    Page<Cliente> findByActivo(Boolean activo, Pageable pageable);

    Page<Cliente> findByDocumentoContainingIgnoreCaseOrNombreCompletoContainingIgnoreCase(
            String documento, String nombreCompleto, Pageable pageable);

    Page<Cliente> findByActivoAndDocumentoContainingIgnoreCaseOrActivoAndNombreCompletoContainingIgnoreCase(
            Boolean activoDocumento, String documento, Boolean activoNombre, String nombreCompleto, Pageable pageable);
}
