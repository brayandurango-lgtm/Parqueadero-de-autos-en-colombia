-- ============================================================================
-- Autos Colombia - Iteracion 2, migracion V3
-- Gestion de celdas: baja logica e integridad de la ocupacion activa
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. Baja logica de la celda
-- ---------------------------------------------------------------------------

ALTER TABLE celda
    ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN celda.activo IS 'Baja logica. Una celda inactiva conserva su estado operativo anterior y deja de ser asignable; no existe un estado INACTIVA en el enum.';
COMMENT ON COLUMN celda.estado IS 'Estado operativo real: DISPONIBLE, OCUPADA o MANTENIMIENTO. OCUPADA solo lo produce una entrada valida.';

-- Sin este indice el filtro de la pantalla de administracion (activas / inactivas) escanea el inventario.
CREATE INDEX idx_celda_activo ON celda (activo);

-- ---------------------------------------------------------------------------
-- 2. Integridad de la ocupacion activa
-- ---------------------------------------------------------------------------

-- Una sola estancia abierta por celda y una sola por vehiculo. Al ser parciales no afectan al historial:
-- las filas CERRADO y CANCELADO siguen conviviendo en la misma tabla sin limite.
CREATE UNIQUE INDEX uk_registro_celda_activo   ON registro (celda_id)    WHERE estado = 'ACTIVO';
CREATE UNIQUE INDEX uk_registro_vehiculo_activo ON registro (vehiculo_id) WHERE estado = 'ACTIVO';

-- Invariante de las dos dimensiones: una celda inactiva no puede declararse ocupada, porque desactivar se
-- rechaza mientras hay un vehiculo adentro.
ALTER TABLE celda
    ADD CONSTRAINT chk_celda_inactiva_no_ocupada
    CHECK (activo = TRUE OR estado <> 'OCUPADA');

-- Busqueda por codigo tal como la normaliza el servicio (sin espacios, en mayusculas). V1 ya garantizaba la
-- unicidad con uk_celda_numero; este indice solo acelera el filtro de la pantalla y no cambia el contrato.
CREATE INDEX idx_celda_numero_lower ON celda (lower(numero));
