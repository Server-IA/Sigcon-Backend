-- V9-ZZU (2026-04-29): emparejamiento de movimientos financieros con
-- JournalEntries (asientos contables modernos), no solo con Vouchers (legacy).
--
-- Contexto: el bug reportado por QA en empresas QA es que el modal "Emparejar
-- con comprobante" salia con dropdown vacio porque solo listaba Vouchers
-- (entidad de pagos legacy). Las empresas QA tienen seeds que generan
-- JournalEntries POSTED (ej. "Asiento apertura QA seed" $5M) que afectan la
-- cuenta bancaria pero no Vouchers, asi que no habia nada que emparejar.
--
-- Solucion: agregar columna matched_journal_entry_id (FK a journal_entries)
-- complementaria a matched_voucher_id existente. Idempotente.

ALTER TABLE financial_movements
    ADD COLUMN IF NOT EXISTS matched_journal_entry_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_finmov_matched_je
    ON financial_movements (matched_journal_entry_id)
    WHERE matched_journal_entry_id IS NOT NULL;

COMMENT ON COLUMN financial_movements.matched_journal_entry_id IS
    'FK opcional a journal_entries. Si esta poblado el movimiento esta emparejado con un asiento contable. Mutuamente exclusivo con matched_voucher_id (no se aplica constraint a nivel BD para no romper datos existentes).';
