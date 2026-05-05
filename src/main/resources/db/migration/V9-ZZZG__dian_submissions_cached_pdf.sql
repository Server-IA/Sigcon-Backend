-- HU-AR-16 E3: cache del PDF representacion grafica DIAN.
-- Idempotente. Se agregan dos columnas a dian_invoice_submissions:
--   cached_pdf BYTEA: bytes del PDF generado y persistido
--   cached_pdf_at TIMESTAMP: marca de tiempo del cache
-- Tras la primera generacion exitosa, el reenvio del PDF lo lee de aqui sin
-- recomputar QR ni iText.
ALTER TABLE dian_invoice_submissions
    ADD COLUMN IF NOT EXISTS cached_pdf BYTEA,
    ADD COLUMN IF NOT EXISTS cached_pdf_at TIMESTAMP NULL;
