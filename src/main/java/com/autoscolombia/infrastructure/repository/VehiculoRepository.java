package com.autoscolombia.infrastructure.repository;

import com.autoscolombia.domain.model.Vehiculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para la entidad Vehiculo.
 */
@Repository
public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {

    Optional<Vehiculo> findByPlaca(String placa);

    List<Vehiculo> findByPlacaContainingIgnoreCaseOrPropietarioNombreContainingIgnoreCaseOrPropietarioDocumentoContaining(
            String placa, String nombre, String documento);
}
