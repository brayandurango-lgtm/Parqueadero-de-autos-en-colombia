package com.autoscolombia.infrastructure.repository;

import com.autoscolombia.domain.enums.EstadoCelda;
import com.autoscolombia.domain.enums.EstadoRegistro;
import com.autoscolombia.domain.enums.TipoVehiculo;
import com.autoscolombia.domain.model.Celda;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para la entidad Celda.
 */
@Repository
public interface CeldaRepository extends JpaRepository<Celda, Long> {

    Optional<Celda> findFirstByTipoVehiculoAndEstadoOrderByNumeroAsc(
            TipoVehiculo tipo, EstadoCelda estado);

    long countByEstado(EstadoCelda estado);

    long countByActivoTrueAndEstado(EstadoCelda estado);

    Optional<Celda> findByNumero(String numero);

    /**
     * Lee una celda adquiriendo un bloqueo de escritura sobre su fila.
     *
     * <p>Toda escritura de celda —asignación por entrada, edición administrativa, transición de mantenimiento
     * y baja lógica— pasa por aquí, de modo que dos operaciones concurrentes sobre la misma celda se
     * serializan y la que gana puede revalidar el estado ya bloqueada.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Celda c where c.id = :id")
    Optional<Celda> buscarConBloqueo(@Param("id") Long id);

    /**
     * Candidatos a recibir un ingreso: activos, en estado DISPONIBLE y sin un registro ACTIVO asociado.
     *
     * <p>Es una lectura sin bloqueo; el servicio bloquea y revalida cada candidato antes de ocupar
     * ({@link #buscarConBloqueo(Long)}). El orden es siempre {@code numero, id} para que dos transacciones
     * que compiten bloqueen en el mismo orden.
     */
    @Query("""
            select c from Celda c
            where c.activo = true
              and c.tipoVehiculo = :tipo
              and c.estado = com.autoscolombia.domain.enums.EstadoCelda.DISPONIBLE
              and not exists (
                    select 1 from Registro r
                    where r.celda.id = c.id
                      and r.estado = com.autoscolombia.domain.enums.EstadoRegistro.ACTIVO
              )
            order by c.numero asc, c.id asc
            """)
    List<Celda> listarAsignablesPorTipo(@Param("tipo") TipoVehiculo tipo);

    /**
     * Igual que {@link #listarAsignablesPorTipo(TipoVehiculo)} pero sin restringir el tipo de vehículo.
     */
    @Query("""
            select c from Celda c
            where c.activo = true
              and c.estado = com.autoscolombia.domain.enums.EstadoCelda.DISPONIBLE
              and not exists (
                    select 1 from Registro r
                    where r.celda.id = c.id
                      and r.estado = com.autoscolombia.domain.enums.EstadoRegistro.ACTIVO
              )
            order by c.numero asc, c.id asc
            """)
    List<Celda> listarAsignables();

    long countByActivoTrue();

    /**
     * Cantidad de celdas realmente asignables. Es la definición que usa el dashboard para «disponibles»:
     * una celda en MANTENIMIENTO, inactiva o con un registro ACTIVO no cuenta, aunque su estado diga
     * DISPONIBLE.
     */
    @Query("""
            select count(c) from Celda c
            where c.activo = true
              and c.estado = com.autoscolombia.domain.enums.EstadoCelda.DISPONIBLE
              and not exists (
                    select 1 from Registro r
                    where r.celda.id = c.id
                      and r.estado = com.autoscolombia.domain.enums.EstadoRegistro.ACTIVO
              )
            """)
    long contarAsignables();

    /**
     * Búsqueda administrativa con los cinco filtros del bloque B3. Los parámetros nulos significan «sin
     * filtro» y {@code pageable} aporta la paginación y el orden.
     */
    @Query("""
            select c from Celda c
            where (:numero = '' or upper(c.numero) like concat('%', upper(:numero), '%'))
              and (:estado is null or c.estado = :estado)
              and (:tipo is null or c.tipoVehiculo = :tipo)
              and (:piso is null or c.piso = :piso)
              and (:activo is null or c.activo = :activo)
            """)
    Page<Celda> buscar(@Param("numero") String numero,
                       @Param("estado") EstadoCelda estado,
                       @Param("tipo") TipoVehiculo tipo,
                       @Param("piso") Integer piso,
                       @Param("activo") Boolean activo,
                       Pageable pageable);

    /**
     * IDs de las celdas que tienen un registro ACTIVO. Sirve para detectar inconsistencias entre el estado
     * declarado de la celda y la ocupación real, sin repararlas.
     */
    @Query("""
            select distinct r.celda.id from Registro r
            where r.estado = :estadoRegistro
            """)
    List<Long> listarIdsConRegistroEn(@Param("estadoRegistro") EstadoRegistro estadoRegistro);
}
