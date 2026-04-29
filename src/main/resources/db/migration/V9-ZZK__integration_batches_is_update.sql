-- =============================================================================
-- V9-ZZK__integration_batches_is_update.sql
--
-- Spec AAEF Bloque W: distinguir lotes iniciales (AAEF) vs Pull+Diff
-- (AgroFusionExchangeUpdate). El AckClient elige el envelope del ACK segun
-- este flag:
--   - is_update=false -> AaefAckDTO camelCase (lote inicial)
--   - is_update=true  -> AgroFusionAcknowledgmentDTO PascalCase (Pull+Diff)
--
-- Tambien guardamos original_exchange_id (apunta al lote padre del envelope)
-- para incluirlo en el ACK PascalCase.
--
-- Idempotente: agrega columnas con DEFAULT, backfilla nulls, fuerza NOT NULL
-- donde aplica. Hibernate ddl-auto=update no podia agregar NOT NULL solo.
-- =============================================================================

DO $$
BEGIN
    -- 1. is_update boolean NOT NULL DEFAULT false
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='integration_batches' AND column_name='is_update'
    ) THEN
        ALTER TABLE integration_batches ADD COLUMN is_update BOOLEAN DEFAULT FALSE;
        RAISE NOTICE 'V9-ZZK: columna is_update agregada a integration_batches.';
    END IF;
    UPDATE integration_batches SET is_update = FALSE WHERE is_update IS NULL;
    ALTER TABLE integration_batches ALTER COLUMN is_update SET NOT NULL;
    ALTER TABLE integration_batches ALTER COLUMN is_update SET DEFAULT FALSE;

    -- 2. original_exchange_id varchar(64) NULL
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='integration_batches' AND column_name='original_exchange_id'
    ) THEN
        ALTER TABLE integration_batches ADD COLUMN original_exchange_id VARCHAR(64);
        RAISE NOTICE 'V9-ZZK: columna original_exchange_id agregada a integration_batches.';
    END IF;
END $$;

-- Indice util para auditoria/reportes que filtren por tipo de lote
CREATE INDEX IF NOT EXISTS idx_integration_batches_is_update
    ON integration_batches (is_update);
