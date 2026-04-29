-- V32: Modulo de Integracion AAEF con AgroFusion (Fase 1).
--
-- Crea las tablas de tracking de lotes y documentos recibidos (integration_batches,
-- integration_transfers, integration_idempotency_keys), agrega columnas nullable
-- 'source', 'external_id', 'exchange_id' a las entidades existentes para permitir
-- diferenciar documentos creados manualmente (source=MANUAL) vs recibidos por la
-- integracion (source=AAEF), y siembra parametros de configuracion de AgroFusion.
--
-- Referencias: RF-INT-12 v4.0 y RF-INT-13 v4.0 del documento de requerimientos del
-- cliente. HUs cubiertas por esta migracion: HU-INT-RF-01, HU-INT-RF-03, HU-INT-RF-09.
--
-- IMPORTANTE: Esta migracion es aditiva. No elimina columnas ni tablas existentes.
-- Todos los campos nuevos en tablas existentes son nullable para no romper datos
-- historicos ni el modo standalone del sistema.

-- ==========================================================================
-- 1. Tabla integration_batches: cabecera de cada lote AAEF recibido
-- ==========================================================================
CREATE TABLE IF NOT EXISTS integration_batches (
    id BIGSERIAL PRIMARY KEY,
    exchange_id VARCHAR(64) NOT NULL,
    standard_version VARCHAR(10) NOT NULL,
    source_system_id VARCHAR(100),
    source_system_name VARCHAR(200),
    source_system_nit VARCHAR(20),
    environment VARCHAR(20),
    generated_by VARCHAR(200),
    period_from DATE,
    period_to DATE,
    total_documents INTEGER NOT NULL DEFAULT 0,
    total_invoices INTEGER NOT NULL DEFAULT 0,
    total_transactions INTEGER NOT NULL DEFAULT 0,
    total_payroll INTEGER NOT NULL DEFAULT 0,
    total_gross_amount NUMERIC(20,2),
    total_taxes NUMERIC(20,2),
    total_net NUMERIC(20,2),
    currency VARCHAR(3),
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    payload_json TEXT NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,
    ack_sent_at TIMESTAMP,
    ack_retry_count INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

-- Valores validos del status (soft check via comentario; JPA enum con @Enumerated.STRING):
-- RECEIVED, PROCESSING, PROCESSED, PARTIAL, FAILED, ACK_PENDING, ACK_SENT, ACK_FAILED

CREATE UNIQUE INDEX IF NOT EXISTS ux_integration_batches_exchange_version
    ON integration_batches(exchange_id, standard_version)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_integration_batches_status
    ON integration_batches(status) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_integration_batches_received_at
    ON integration_batches(received_at) WHERE deleted_at IS NULL;

-- ==========================================================================
-- 2. Tabla integration_transfers: tracking por documento individual
-- ==========================================================================
CREATE TABLE IF NOT EXISTS integration_transfers (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    document_id VARCHAR(100) NOT NULL,
    document_type VARCHAR(30) NOT NULL,
    transfer_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    accounting_entry_id BIGINT,
    error_code VARCHAR(50),
    error_message VARCHAR(500),
    retry_allowed BOOLEAN NOT NULL DEFAULT true,
    is_update BOOLEAN NOT NULL DEFAULT false,
    retry_count INTEGER NOT NULL DEFAULT 0,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP,
    CONSTRAINT fk_integration_transfers_batch
        FOREIGN KEY (batch_id) REFERENCES integration_batches(id)
);

-- Valores validos:
--   transfer_status: PENDING, PROCESSED, FAILED, RETRYING
--   document_type:   INVOICE, TRANSACTION, PAYROLL

CREATE INDEX IF NOT EXISTS idx_integration_transfers_batch
    ON integration_transfers(batch_id) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_integration_transfers_document
    ON integration_transfers(document_id) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_integration_transfers_status
    ON integration_transfers(transfer_status) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_integration_transfers_accounting_entry
    ON integration_transfers(accounting_entry_id) WHERE deleted_at IS NULL;

-- ==========================================================================
-- 3. Tabla integration_idempotency_keys: garantia extra de no-duplicados
-- ==========================================================================
-- Esta tabla rastrea cada intento de recepcion de un exchangeId, incluso si
-- el insert en integration_batches falla. Garantiza que un reenvio con el mismo
-- exchangeId+standardVersion siempre retorne 409 Conflict.
CREATE TABLE IF NOT EXISTS integration_idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    exchange_id VARCHAR(64) NOT NULL,
    standard_version VARCHAR(10) NOT NULL,
    batch_id BIGINT,
    first_received_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_attempt_at TIMESTAMP NOT NULL DEFAULT NOW(),
    attempt_count INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_idempotency_batch
        FOREIGN KEY (batch_id) REFERENCES integration_batches(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_idempotency_exchange_version
    ON integration_idempotency_keys(exchange_id, standard_version);

-- ==========================================================================
-- 4. Columnas nuevas en entidades existentes (nullable)
-- ==========================================================================
-- Estas columnas permiten diferenciar el origen de cada documento:
--   source = 'MANUAL' -> creado por contador via frontend (por defecto)
--   source = 'AAEF'   -> recibido via integracion con AgroFusion
-- external_id: identificador del documento en el sistema origen (DocumentId AAEF)
-- exchange_id: identificador del lote AAEF del que provino el documento

DO $$
BEGIN
    -- sales_invoices
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'sales_invoices') THEN
        ALTER TABLE sales_invoices ADD COLUMN IF NOT EXISTS source VARCHAR(20) DEFAULT 'MANUAL';
        ALTER TABLE sales_invoices ADD COLUMN IF NOT EXISTS external_id VARCHAR(100);
        ALTER TABLE sales_invoices ADD COLUMN IF NOT EXISTS exchange_id VARCHAR(64);
    END IF;

    -- invoices (AP)
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'invoices') THEN
        ALTER TABLE invoices ADD COLUMN IF NOT EXISTS source VARCHAR(20) DEFAULT 'MANUAL';
        ALTER TABLE invoices ADD COLUMN IF NOT EXISTS external_id VARCHAR(100);
        ALTER TABLE invoices ADD COLUMN IF NOT EXISTS exchange_id VARCHAR(64);
    END IF;

    -- ar_payments
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ar_payments') THEN
        ALTER TABLE ar_payments ADD COLUMN IF NOT EXISTS source VARCHAR(20) DEFAULT 'MANUAL';
        ALTER TABLE ar_payments ADD COLUMN IF NOT EXISTS external_id VARCHAR(100);
        ALTER TABLE ar_payments ADD COLUMN IF NOT EXISTS exchange_id VARCHAR(64);
    END IF;

    -- ap_payments
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ap_payments') THEN
        ALTER TABLE ap_payments ADD COLUMN IF NOT EXISTS source VARCHAR(20) DEFAULT 'MANUAL';
        ALTER TABLE ap_payments ADD COLUMN IF NOT EXISTS external_id VARCHAR(100);
        ALTER TABLE ap_payments ADD COLUMN IF NOT EXISTS exchange_id VARCHAR(64);
    END IF;

    -- ar_advances
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ar_advances') THEN
        ALTER TABLE ar_advances ADD COLUMN IF NOT EXISTS source VARCHAR(20) DEFAULT 'MANUAL';
        ALTER TABLE ar_advances ADD COLUMN IF NOT EXISTS external_id VARCHAR(100);
        ALTER TABLE ar_advances ADD COLUMN IF NOT EXISTS exchange_id VARCHAR(64);
    END IF;

    -- ap_advances
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ap_advances') THEN
        ALTER TABLE ap_advances ADD COLUMN IF NOT EXISTS source VARCHAR(20) DEFAULT 'MANUAL';
        ALTER TABLE ap_advances ADD COLUMN IF NOT EXISTS external_id VARCHAR(100);
        ALTER TABLE ap_advances ADD COLUMN IF NOT EXISTS exchange_id VARCHAR(64);
    END IF;

    -- ar_credit_debit_notes
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ar_credit_debit_notes') THEN
        ALTER TABLE ar_credit_debit_notes ADD COLUMN IF NOT EXISTS source VARCHAR(20) DEFAULT 'MANUAL';
        ALTER TABLE ar_credit_debit_notes ADD COLUMN IF NOT EXISTS external_id VARCHAR(100);
        ALTER TABLE ar_credit_debit_notes ADD COLUMN IF NOT EXISTS exchange_id VARCHAR(64);
    END IF;

    -- ap_credit_debit_notes
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ap_credit_debit_notes') THEN
        ALTER TABLE ap_credit_debit_notes ADD COLUMN IF NOT EXISTS source VARCHAR(20) DEFAULT 'MANUAL';
        ALTER TABLE ap_credit_debit_notes ADD COLUMN IF NOT EXISTS external_id VARCHAR(100);
        ALTER TABLE ap_credit_debit_notes ADD COLUMN IF NOT EXISTS exchange_id VARCHAR(64);
    END IF;

    -- third_parties: solo campo source (no external_id porque el NIT ya identifica)
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'third_parties') THEN
        ALTER TABLE third_parties ADD COLUMN IF NOT EXISTS source VARCHAR(20) DEFAULT 'MANUAL';
    END IF;
END $$;

-- ==========================================================================
-- 5. Parametros de configuracion de AgroFusion
-- ==========================================================================
-- Configuracion inicial de la integracion. Los valores por defecto son placeholders
-- y deben ajustarse en produccion via UI de parametros del sistema.

-- QA-BLOQUE-AN (2026-04-29): agregado company_id=1 explicito a los INSERTs.
-- V10-A pone parameters.company_id NOT NULL antes de que V32 corra (V10-A < V32
-- en orden lexicografico). Sin company_id=1 los INSERTs crashean en BD limpia
-- con "null value violates not-null constraint" -> Application run failed.
INSERT INTO parameters (company_id, name, value, description, category, status, created_at, updated_at)
SELECT 1, 'AGROFUSION_API_KEY',
       'changeme-in-production-' || md5(random()::text),
       'API Key para autenticar lotes AAEF entrantes de AgroFusion (header X-API-Key)',
       'INTEGRATION_AGROFUSION', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM parameters WHERE name = 'AGROFUSION_API_KEY' AND company_id = 1 AND deleted_at IS NULL
);

INSERT INTO parameters (company_id, name, value, description, category, status, created_at, updated_at)
SELECT 1, 'AGROFUSION_ACK_CALLBACK_URL',
       'https://api.agrofusion.co/integrations/aaef/ack',
       'URL del callback donde SIGCON envia el ACK tras procesar un lote AAEF',
       'INTEGRATION_AGROFUSION', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM parameters WHERE name = 'AGROFUSION_ACK_CALLBACK_URL' AND company_id = 1 AND deleted_at IS NULL
);

INSERT INTO parameters (company_id, name, value, description, category, status, created_at, updated_at)
SELECT 1, 'AGROFUSION_JWKS_URL',
       'https://sso.agrofusion.co/.well-known/jwks.json',
       'URL del JWKS de AgroFusion para validacion JWT RS256 (usado en Fase 6)',
       'INTEGRATION_AGROFUSION', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM parameters WHERE name = 'AGROFUSION_JWKS_URL' AND company_id = 1 AND deleted_at IS NULL
);

INSERT INTO parameters (company_id, name, value, description, category, status, created_at, updated_at)
SELECT 1, 'AGROFUSION_JWT_ISSUER',
       'https://sso.agrofusion.co',
       'Issuer esperado en el JWT emitido por el SSO de AgroFusion (usado en Fase 6)',
       'INTEGRATION_AGROFUSION', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM parameters WHERE name = 'AGROFUSION_JWT_ISSUER' AND company_id = 1 AND deleted_at IS NULL
);

INSERT INTO parameters (company_id, name, value, description, category, status, created_at, updated_at)
SELECT 1, 'AGROFUSION_MAX_BATCH_SIZE_MB',
       '20',
       'Tamano maximo permitido por lote AAEF en megabytes (RF-INT-12)',
       'INTEGRATION_AGROFUSION', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM parameters WHERE name = 'AGROFUSION_MAX_BATCH_SIZE_MB' AND company_id = 1 AND deleted_at IS NULL
);

INSERT INTO parameters (company_id, name, value, description, category, status, created_at, updated_at)
SELECT 1, 'AGROFUSION_AUTH_MODE',
       'API_KEY',
       'Modo de autenticacion activo para los endpoints AAEF: API_KEY | JWT | BOTH',
       'INTEGRATION_AGROFUSION', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM parameters WHERE name = 'AGROFUSION_AUTH_MODE' AND company_id = 1 AND deleted_at IS NULL
);
