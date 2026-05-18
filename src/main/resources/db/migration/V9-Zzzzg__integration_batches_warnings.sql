-- QA Bloque BK (HU-INT-RF-02 E5 + HU-INT-RF-03 E4, 2026-05-18):
-- Persistir los warnings informativos detectados en la recepcion del lote AAEF
-- (UpdatedAt ausente, DocumentId duplicado intra-batch) para que la UI
-- /integracion/lotes/{id} pueda mostrarlos al QA/Soporte despues del 202 inicial.
--
-- Antes los warnings solo se devolvian en el response del POST /aaef y se
-- perdian si el cliente no los capturaba. Ahora se almacenan como JSON array
-- de strings en TEXT, sin perder el response inmediato.
--
-- Idempotente: ADD COLUMN IF NOT EXISTS.

ALTER TABLE integration_batches
    ADD COLUMN IF NOT EXISTS warnings TEXT;

COMMENT ON COLUMN integration_batches.warnings IS
    'JSON array de warnings informativos (HU-INT-RF-02 E5 / HU-INT-RF-03 E4). '
    'NULL o vacio = sin warnings. Se popula durante AaefReceiverService.receive().';
