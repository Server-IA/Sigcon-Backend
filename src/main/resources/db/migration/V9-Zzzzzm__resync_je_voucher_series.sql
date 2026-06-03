-- =============================================================================
-- Bug ACT-RF-01 (2026-06-01): re-sincronizar el contador de la serie 'JE'
-- (voucher_series_config) con el MAX(entry_number) real de cada empresa.
--
-- Causa: los seeds (apertura, demo, QA) y otros flujos insertan journal_entries
-- DIRECTAMENTE con entry_number hardcoded, SIN consumir la serie. Eso deja
-- current_number atrasado respecto al MAX real. El siguiente consumeNext("JE")
-- devuelve un numero que YA existe -> "duplicate key value violates unique
-- constraint uk_journal_entries_company_fy_num". El sintoma que reporto QA fue el
-- opaco "Transaction silently rolled back" al registrar un activo a CREDITO (su
-- factura AP genera un asiento contable).
--
-- El codigo (JournalEntryService.assignNextJournalNumber) ya hace este self-heal
-- en cada creacion de asiento; esta migracion lo corrige ademas de forma
-- deterministica al arranque, para el dato ya existente en local y produccion.
--
-- Idempotente: GREATEST nunca retrocede el contador.
-- =============================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_name = 'voucher_series_config')
       AND EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_name = 'journal_entries') THEN

        UPDATE voucher_series_config vsc
        SET current_number = GREATEST(
                vsc.current_number,
                COALESCE((SELECT MAX(je.entry_number)
                          FROM journal_entries je
                          WHERE je.company_id = vsc.company_id
                            AND je.deleted_at IS NULL), 0)
            )
        WHERE UPPER(vsc.voucher_type) = 'JE'
          AND vsc.deleted_at IS NULL;

        RAISE NOTICE 'V9-Zzzzzm: contadores de serie JE re-sincronizados al MAX(entry_number) por empresa.';
    END IF;
END $$;
