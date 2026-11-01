package com.autoscolombia.infrastructure.repository;

import com.autoscolombia.domain.enums.EstadoRegistro;
import com.autoscolombia.domain.model.Registro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    Optional<Registro> findByCeldaIdAndEstado(Long celdaId, EstadoRegistro estado);

    boolean existsByCeldaIdAndEstado(Long celdaId, EstadoRegistro estado);

    /**
     * Filas ACTIVAS repetidas sobre la misma celda. En PostgreSQL esto es imposible por el índice único
     * parcial de {@code V3}; en H2 lo controla el servicio, y esta consulta deja la anomalía a la vista en
     * lugar de elegir cuál conservar.
     */
    @Query("""
            select r from Registro r
            where r.estado = com.autoscolombia.domain.enums.EstadoRegistro.ACTIVO
              and r.celda.id in (
                    select r2.celda.id from Registro r2
                    where r2.estado = com.autoscolombia.domain.enums.EstadoRegistro.ACTIVO
                    group by r2.celda.id having count(r2.id) > 1
              )
            order by r.celda.id asc, r.id asc
            """)
    List<Registro> listarActivosDuplicadosPorCelda();

    /**
     * Filas ACTIVAS repetidas sobre el mismo vehículo: la misma anomalía que evita el índice único parcial
     * por {@code vehiculo_id}.
     */
    @Query("""
            select r from Registro r
            where r.estado = com.autoscolombia.domain.enums.EstadoRegistro.ACTIVO
              and r.vehiculo.id in (
                    select r2.vehiculo.id from Registro r2
                    where r2.estado = com.autoscolombia.domain.enums.EstadoRegistro.ACTIVO
                    group by r2.vehiculo.id having count(r2.id) > 1
              )
            order by r.vehiculo.id asc, r.id asc
            """)
    List<Registro> listarActivosDuplicadosPorVehiculo();

    long countByEstado(EstadoRegistro estado);

    List<Registro> findByEstadoOrderByFechaHoraEntradaDesc(EstadoRegistro estado);

    List<Registro> findByFechaHoraEntradaBetween(LocalDateTime desde, LocalDateTime hasta);

    long countByFechaHoraEntradaBetween(LocalDateTime desde, LocalDateTime hasta);
}
