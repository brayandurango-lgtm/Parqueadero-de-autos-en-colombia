package com.autoscolombia.infrastructure.repository;

import com.autoscolombia.domain.enums.TipoUsuario;
import com.autoscolombia.domain.model.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para la entidad Usuario.
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correo);

    /**
     * Consulta por documento, para comprobar unicidad sin cargar la tabla entera.
     */
    Optional<Usuario> findByDocumento(String documento);

    /**
     * Conjunto de cuentas del rol indicado que están activas, bloqueadas para escritura dentro de la
     * transacción. Se usa para decidir si una desactivación o una degradación dejaría al sistema sin
     * administrador activo.
     *
     * <p>El bloqueo se traduce a {@code SELECT ... FOR UPDATE} sobre las filas del conjunto. En H2 solo
     * demuestra que la consulta es válida; la garantía frente a transacciones concurrentes pertenece a
     * PostgreSQL y no se ha verificado ahí.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Usuario> findByTipoAndActivo(TipoUsuario tipo, Boolean activo);
}
