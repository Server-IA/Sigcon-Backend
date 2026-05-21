-- =====================================================================
-- BNK-HU-065 — Verificacion de integridad de la cadena del log de auditoria.
-- Tabla append-only que registra cada ejecucion del verificador (nocturno
-- o bajo demanda). NO multi-tenant: la verificacion es global del sistema.
-- Idempotente (CREATE TABLE IF NOT EXISTS).
-- =====================================================================

CREATE TABLE IF NOT EXISTS log_integridad_ejecuciones (
    id                  BIGSERIAL PRIMARY KEY,
    executed_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    total_verified      BIGINT NOT NULL DEFAULT 0,
    result              VARCHAR(20) NOT NULL,          -- OK | RUPTURA | ERROR
    first_broken_id     BIGINT,                        -- id del primer registro inconsistente
    chain_breaks        BIGINT NOT NULL DEFAULT 0,     -- rupturas de encadenamiento (previous_hash)
    content_mismatches  BIGINT NOT NULL DEFAULT 0,     -- hashes que no recalculan (posible manipulacion)
    duration_ms         BIGINT NOT NULL DEFAULT 0,
    trigger_source      VARCHAR(20) NOT NULL DEFAULT 'SCHEDULER', -- SCHEDULER | MANUAL
    triggered_by        VARCHAR(255),
    detail              TEXT
);

CREATE INDEX IF NOT EXISTS idx_log_integridad_executed_at
    ON log_integridad_ejecuciones (executed_at DESC);

COMMENT ON TABLE log_integridad_ejecuciones IS 'BNK-HU-065 E4: bitacora de ejecuciones del verificador de integridad del log de auditoria';
