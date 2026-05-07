-- =============================================================================
-- V9-ZZZQ: Tabla goods_receipt_invoice_links para soportar vinculacion N:M
-- entre recepciones y facturas (HU-AP-19 E1/E4/E5/E6).
-- ----------------------------------------------------------------------------
-- QA-BLOQUE-AY (2026-05-06): el reporte QA reportaba fallos en HU-AP-19:
--   - E1: no se puede vincular si hay multiples recepciones de la misma OC
--   - E4: la recepcion ya facturada permite vincular nueva (deberia bloquear)
--   - E5: factura inferior al monto recibido no avisa al usuario
--   - E6: no se puede vincular una recepcion parcialmente a varias facturas
--
-- El modelo previo (goods_receipts.invoice_id) solo soportaba vinculacion 1:1.
-- Para soportar parcialidad y multi-vinculacion sin romper el modelo legacy:
--   1) Tabla nueva con (receipt_id, invoice_id, invoiced_amount).
--   2) Campo computado total_invoiced = SUM(invoiced_amount) por receipt.
--   3) goods_receipts.invoice_id sigue existiendo para compat (apunta al
--      primer link) pero la fuente real es esta tabla.
--
-- Idempotente con CREATE TABLE IF NOT EXISTS y backfill defensivo.
-- =============================================================================

CREATE TABLE IF NOT EXISTS goods_receipt_invoice_links (
    id            BIGSERIAL PRIMARY KEY,
    company_id    BIGINT NOT NULL,
    receipt_id    BIGINT NOT NULL,
    invoice_id    BIGINT NOT NULL,
    invoiced_amount NUMERIC(20,2) NOT NULL,
    notes         VARCHAR(500),
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMP NULL,
    CONSTRAINT fk_grilink_receipt FOREIGN KEY (receipt_id) REFERENCES goods_receipts(id),
    CONSTRAINT fk_grilink_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    CONSTRAINT fk_grilink_company FOREIGN KEY (company_id) REFERENCES companies(id)
);

CREATE INDEX IF NOT EXISTS idx_grilink_receipt ON goods_receipt_invoice_links(receipt_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_grilink_invoice ON goods_receipt_invoice_links(invoice_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_grilink_company ON goods_receipt_invoice_links(company_id);

-- Backfill defensivo: por cada goods_receipt con invoice_id != NULL, crear el
-- link equivalente con invoiced_amount = total de la factura (asume que el link
-- legacy 1:1 cubre la totalidad de la factura).
INSERT INTO goods_receipt_invoice_links (company_id, receipt_id, invoice_id, invoiced_amount, notes, created_at, updated_at)
SELECT gr.company_id, gr.id, gr.invoice_id, COALESCE(i.total_amount, 0),
       'Backfill V9-ZZZQ: link legacy 1:1', NOW(), NOW()
FROM goods_receipts gr
LEFT JOIN invoices i ON i.id = gr.invoice_id
WHERE gr.invoice_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM goods_receipt_invoice_links l
      WHERE l.receipt_id = gr.id
        AND l.invoice_id = gr.invoice_id
        AND l.deleted_at IS NULL
  );

DO $$
DECLARE c BIGINT;
BEGIN
    SELECT COUNT(*) INTO c FROM goods_receipt_invoice_links WHERE deleted_at IS NULL;
    RAISE NOTICE 'V9-ZZZQ: % links activos en goods_receipt_invoice_links', c;
END $$;
