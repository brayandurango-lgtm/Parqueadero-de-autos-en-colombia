package com.autoscolombia.infrastructure.repository;

import com.autoscolombia.domain.model.Vehiculo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para la entidad Vehiculo.
 */
@Repository
public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {

    Optional<Vehiculo> findByPlaca(String placa);

    /**
     * Lee el vehículo adquiriendo un bloqueo de escritura sobre su fila.
     *
     * <p>La entrada lo pide antes de comprobar si el vehículo ya tiene un registro ACTIVO, de modo que dos
     * ingresos simultáneos del mismo vehículo se serializan y el que continúa revalida sobre la fila ya
     * bloqueada. Es la defensa de la aplicación; en PostgreSQL la refuerza el índice único parcial
     * {@code uk_registro_vehiculo_activo} creado por {@code V3__gestion_celdas_e_integridad_ocupacion.sql}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehiculo v where v.placa = :placa")
    Optional<Vehiculo> buscarConBloqueoPorPlaca(@Param("placa") String placa);

    /** Vehículos cuyo titular actual es el cliente indicado. */
    List<Vehiculo> findByClienteId(Long clienteId);

    /** Cuántos vehículos siguen apuntando a un cliente: evita dejar titularidades huérfanas al reactivar. */
    long countByClienteId(Long clienteId);

    /** Búsqueda por los datos del cliente vinculado, para que el criterio del panel también cubra titularidades nuevas. */
    List<Vehiculo> findByClienteDocumentoContainingIgnoreCaseOrClienteNombreCompletoContainingIgnoreCase(
            String documento, String nombreCompleto);

    List<Vehiculo> findByPlacaContainingIgnoreCaseOrPropietarioNombreContainingIgnoreCaseOrPropietarioDocumentoContaining(
            String placa, String nombre, String documento);
}
