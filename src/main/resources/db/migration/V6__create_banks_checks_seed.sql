CREATE TABLE IF NOT EXISTS checks (
    id BIGSERIAL PRIMARY KEY,
    checkbooks_id BIGINT NULL,
    number_check INT NOT NULL,
    beneficiary VARCHAR(200) NOT NULL,
    value NUMERIC(20, 2) NOT NULL,
    concept VARCHAR(200) NOT NULL,
    issue_date DATE NOT NULL,
    collection_date DATE,
    type_check VARCHAR(16) NOT NULL DEFAULT 'FISICO',
    status_check VARCHAR(16) NOT NULL DEFAULT 'EMITIDO',
    financial_movement_id BIGINT NULL,
    observations TEXT,
    support_document_path VARCHAR(500),
    support_document_mime VARCHAR(50),
    void_reason VARCHAR(500),
    voided_at TIMESTAMP,
    incident_type VARCHAR(16),
    incident_date DATE,
    incident_detail TEXT,
    incident_actions VARCHAR(500),
    block_payment BOOLEAN NOT NULL DEFAULT FALSE,
    conciliation_method VARCHAR(16),
    collection_reference VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL
);

ALTER TABLE checks DROP CONSTRAINT IF EXISTS fk_checks_financial_movement;
ALTER TABLE checks ALTER COLUMN checkbooks_id DROP NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_checks_checkbook'
    ) THEN
        ALTER TABLE checks
        ADD CONSTRAINT fk_checks_checkbook
        FOREIGN KEY (checkbooks_id) REFERENCES checkbooks(id);
    END IF;
END $$;

INSERT INTO checks (
    checkbooks_id,
    number_check,
    beneficiary,
    value,
    concept,
    issue_date,
    collection_date,
    type_check,
    status_check,
    financial_movement_id,
    observations,
    support_document_path,
    support_document_mime,
    block_payment,
    created_at,
    updated_at
)
SELECT
    (SELECT cb.id FROM checkbooks cb ORDER BY cb.id LIMIT 1),
    1001,
    'PROVEEDOR DEMO UNO SAS',
    1500000.00,
    'Pago de servicios profesionales',
    DATE '2026-03-18',
    NULL,
    'FISICO',
    'EMITIDO',
    NULL,
    'Cheque fisico de prueba',
    NULL,
    NULL,
    FALSE,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM checks c
    WHERE c.deleted_at IS NULL
      AND c.number_check = 1001
      AND c.beneficiary = 'PROVEEDOR DEMO UNO SAS'
      AND c.issue_date = DATE '2026-03-18'
);

INSERT INTO checks (
    checkbooks_id,
    number_check,
    beneficiary,
    value,
    concept,
    issue_date,
    collection_date,
    type_check,
    status_check,
    financial_movement_id,
    observations,
    support_document_path,
    support_document_mime,
    block_payment,
    created_at,
    updated_at
)
SELECT
    (SELECT cb.id FROM checkbooks cb ORDER BY cb.id LIMIT 1),
    1002,
    'PROVEEDOR DEMO DOS LTDA',
    980000.00,
    'Pago de soporte tecnico',
    DATE '2026-03-18',
    NULL,
    'VIRTUAL',
    'EMITIDO',
    NULL,
    'Cheque virtual de prueba',
    '/documentos/cheques/virtuales/2026/03/CHEQUE_1002_SEED.pdf',
    'application/pdf',
    FALSE,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM checks c
    WHERE c.deleted_at IS NULL
      AND c.number_check = 1002
      AND c.beneficiary = 'PROVEEDOR DEMO DOS LTDA'
      AND c.issue_date = DATE '2026-03-18'
);
