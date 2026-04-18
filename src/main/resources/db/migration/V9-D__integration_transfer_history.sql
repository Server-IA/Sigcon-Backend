-- V9-D: Tabla integration_transfer_history (HU-INT-RF-15 E4).
--
-- Captura el historial completo de cada intento de procesamiento de un transfer:
-- intento original, retries manuales (HU-INT-RF-15), y resultado de cada uno.
--
-- Antes de esta migracion, solo se conservaba retry_count en integration_transfers,
-- sin contexto de quien reintento, cuando, con que resultado o que error tuvo cada
-- intento. Con esta tabla se puede auditar el ciclo de vida completo del transfer.
--
-- Append-only en spirit: aunque tiene deleted_at por consistencia con el resto del
-- proyecto, el frontend nunca permite borrarlo y el servicio solo INSERTA. Sirve
-- como evidencia auditable.

CREATE TABLE IF NOT EXISTS integration_transfer_history (
    id                  BIGSERIAL PRIMARY KEY,
    transfer_id         BIGINT NOT NULL REFERENCES integration_transfers(id) ON DELETE CASCADE,
    -- Numero de intento (0 = procesamiento inicial, 1 = primer retry, 2 = segundo, etc.)
    attempt_number      INT NOT NULL,
    -- Resultado de este intento: SUCCESS / FAILED / RETRYING / SKIPPED
    result_status       VARCHAR(30) NOT NULL,
    -- Codigo de error si fallo (replicado del transfer en ese momento)
    error_code          VARCHAR(50),
    error_message       VARCHAR(1000),
    -- ID del JE generado si fue exitoso
    accounting_entry_id BIGINT,
    -- Trigger del intento: SYSTEM (procesamiento inicial / scheduler) o MANUAL (UI)
    trigger_source      VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
    -- Quien lo gatillo: 'system' para automatico, username para retry manual
    triggered_by        VARCHAR(100),
    -- Nota libre del usuario al hacer retry manual
    user_note           VARCHAR(500),
    -- Si se origino un nuevo batch sintetico (caso retry), su ID
    new_batch_id        BIGINT,
    -- Cuando ocurrio este intento
    occurred_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP NULL
);

-- Indices para queries comunes del frontend
CREATE INDEX IF NOT EXISTS idx_transfer_history_transfer
    ON integration_transfer_history (transfer_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_transfer_history_result
    ON integration_transfer_history (result_status);

COMMENT ON TABLE integration_transfer_history IS
    'V9-D / HU-INT-RF-15 E4: historial inmutable de intentos por transfer. '
    'Cada fila representa un procesamiento (inicial o retry). Append-only.';
COMMENT ON COLUMN integration_transfer_history.attempt_number IS
    '0 = intento inicial (cuando se proceso el batch original); 1+ = retries manuales o automaticos';
COMMENT ON COLUMN integration_transfer_history.trigger_source IS
    'SYSTEM: procesamiento async automatico; MANUAL: retry desde UI por admin';
