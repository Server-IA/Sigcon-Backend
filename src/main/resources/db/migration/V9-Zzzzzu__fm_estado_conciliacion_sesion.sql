-- =============================================================================
-- QA Bloque BNK (2026-06-03) Bug 1 — Estado de conciliacion AISLADO por sesion.
--
-- Decision del lider: durante la conciliacion los estados conciliado/no-conciliado
-- se manejan en un entorno AISLADO que NO toca la "trazabilidad actual del sistema"
-- (la columna oficial `estado_conciliacion`, que leen DIAN/TRM/otros modulos). El
-- estado OFICIAL de los registros solo se actualiza cuando el usuario CIERRA + FIRMA
-- la conciliacion. Mientras la sesion este abierta (BORRADOR/REABIERTA), el motor de
-- matching y el emparejamiento manual escriben SOLO en `estado_conciliacion_sesion`.
--
-- Idempotente. Backfill: copia el estado oficial actual al de sesion para que las
-- sesiones en curso (si las hubiera) sigan funcionando sin saltos.
-- =============================================================================
ALTER TABLE financial_movements
    ADD COLUMN IF NOT EXISTS estado_conciliacion_sesion VARCHAR(20);

UPDATE financial_movements
    SET estado_conciliacion_sesion = COALESCE(estado_conciliacion, 'NO_CONCILIADO')
    WHERE estado_conciliacion_sesion IS NULL;
