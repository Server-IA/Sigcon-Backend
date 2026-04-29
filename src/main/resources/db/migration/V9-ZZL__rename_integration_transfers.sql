-- =============================================================================
-- V9-ZZL__rename_integration_transfers.sql
--
-- Spec AAEF Bloque W (item cosmetico cerrado): renombrar la tabla
-- `integration_transfers` -> `af_accounting_transfers` para alinearse con
-- la nomenclatura exigida por la spec del modulo de integracion AAEF.
--
-- ALTER TABLE RENAME es atomico: preserva datos, indices, FKs y constraints.
-- Las FKs que apuntan a esta tabla (integration_transfer_history.transfer_id)
-- se mantienen porque referencian por OID interno, no por nombre de tabla.
--
-- Idempotente: si la tabla ya se renombro (o si el nombre nuevo ya existe),
-- el script no hace nada.
-- =============================================================================

DO $$
BEGIN
    -- 1. Renombrar la tabla principal si todavia existe con el nombre viejo.
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name='integration_transfers' AND table_schema='public'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name='af_accounting_transfers' AND table_schema='public'
    ) THEN
        ALTER TABLE integration_transfers RENAME TO af_accounting_transfers;
        RAISE NOTICE 'V9-ZZL: integration_transfers renombrada a af_accounting_transfers.';
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name='af_accounting_transfers' AND table_schema='public'
    ) THEN
        RAISE NOTICE 'V9-ZZL: af_accounting_transfers ya existe, nada que renombrar.';
    ELSE
        RAISE NOTICE 'V9-ZZL: integration_transfers no existe (BD limpia o sin V32 corrido).';
    END IF;

    -- 2. Renombrar indices que tenian el prefix viejo.
    -- Si el indice viejo existe pero el nuevo TAMBIEN (porque Hibernate ddl-auto lo
    -- recreo automaticamente), dropeamos el viejo. Si solo existe el viejo,
    -- renombramos. Cubre los 2 escenarios sin romper.
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname='idx_integration_transfers_batch') THEN
        IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname='idx_af_accounting_transfers_batch') THEN
            DROP INDEX idx_integration_transfers_batch;
        ELSE
            ALTER INDEX idx_integration_transfers_batch RENAME TO idx_af_accounting_transfers_batch;
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname='idx_integration_transfers_document') THEN
        IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname='idx_af_accounting_transfers_document') THEN
            DROP INDEX idx_integration_transfers_document;
        ELSE
            ALTER INDEX idx_integration_transfers_document RENAME TO idx_af_accounting_transfers_document;
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname='idx_integration_transfers_status') THEN
        IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname='idx_af_accounting_transfers_status') THEN
            DROP INDEX idx_integration_transfers_status;
        ELSE
            ALTER INDEX idx_integration_transfers_status RENAME TO idx_af_accounting_transfers_status;
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname='idx_integration_transfers_accounting_entry') THEN
        IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname='idx_af_accounting_transfers_accounting_entry') THEN
            DROP INDEX idx_integration_transfers_accounting_entry;
        ELSE
            ALTER INDEX idx_integration_transfers_accounting_entry RENAME TO idx_af_accounting_transfers_accounting_entry;
        END IF;
    END IF;

    -- 3. Renombrar la FK constraint si existe EN la tabla nueva (puede que
    -- Hibernate ya haya recreado la constraint con otro nombre, o la tabla
    -- vino limpia desde DDL inicial sin el prefijo viejo).
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name='fk_integration_transfers_batch'
          AND table_name='af_accounting_transfers'
    ) THEN
        ALTER TABLE af_accounting_transfers
            RENAME CONSTRAINT fk_integration_transfers_batch
            TO fk_af_accounting_transfers_batch;
    END IF;
END $$;
