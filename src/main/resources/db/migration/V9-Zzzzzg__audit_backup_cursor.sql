-- ============================================================================
-- HU-AU-09 — Transferencia de logs de auditoria a BD EXTERNA de respaldo.
--
-- Cursor LOCAL de progreso de transferencia. Se usa un cursor (last_transferred_id)
-- en lugar de un flag `enviado` por fila porque `audit_logs` es APPEND-ONLY
-- (HU-AU-01): no se puede mutar ningun registro existente sin romper el principio
-- de inmutabilidad ni la cadena de hash. El cursor avanza solo cuando un lote se
-- confirma INTEGRO en la BD externa.
--
-- Tabla de UNA sola fila (id=1). last_transferred_id = mayor audit_logs.id ya
-- respaldado con exito. La transferencia toma audit_logs WHERE id > cursor.
--
-- NOTA (Bloque AW): nombre con 'z' minuscula (V9-Zzzzz*) para que ordene DESPUES
-- de V9-Z__multi_tenant_final_fixes en el cargador lexical del DataInitializer.
-- ============================================================================

CREATE TABLE IF NOT EXISTS audit_backup_cursor (
    id                   INTEGER      PRIMARY KEY,
    last_transferred_id  BIGINT       NOT NULL DEFAULT 0,
    last_run_at          TIMESTAMP,
    last_status          VARCHAR(20),
    last_message         VARCHAR(500),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Fila unica de control (idempotente).
INSERT INTO audit_backup_cursor (id, last_transferred_id, updated_at)
SELECT 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM audit_backup_cursor WHERE id = 1);
