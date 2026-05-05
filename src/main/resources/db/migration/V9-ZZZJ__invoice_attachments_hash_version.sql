-- HU-AP-12 E3/E4 (Bloque AR, 2026-05-04): agrega columnas para deteccion de
-- duplicados por contenido (file_hash SHA-256) y versionado de adjuntos.
-- Idempotente con IF NOT EXISTS.
ALTER TABLE invoice_attachments
    ADD COLUMN IF NOT EXISTS file_hash VARCHAR(64);

ALTER TABLE invoice_attachments
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE invoice_attachments
    ADD COLUMN IF NOT EXISTS replaced_by_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_invoice_attachments_hash_company
    ON invoice_attachments(company_id, file_hash)
    WHERE deleted_at IS NULL AND file_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_invoice_attachments_replaced_by
    ON invoice_attachments(replaced_by_id);
