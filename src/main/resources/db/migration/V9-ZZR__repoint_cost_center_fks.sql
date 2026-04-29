-- V9-ZZR - 2026-04-28
-- Bug: cuando V9-ZZC u otros seeds re-ejecutan en BD existente, los CCs viejos
-- se soft-deletean y se crean nuevos con mismo `code`, pero las FKs en
-- `bank_accounts`, `accounting_accounts`, `cash`, `journal_entry_lines`
-- mantienen el id viejo. Resultado: Hibernate `@Where(deleted_at IS NULL)`
-- falla con "Unable to find CostCenter with id N".
--
-- Esta migracion es defensiva: para cada FK live que apunta a un CC
-- soft-deletado, busca el CC activo equivalente en el mismo company_id
-- (mismo `code`) y reapunta. Idempotente: si no hay casos, no hace nada.

DO $$
BEGIN
    -- 1. bank_accounts.cost_center_id
    UPDATE bank_accounts ba
       SET cost_center_id = (
           SELECT cc_new.id FROM cost_centers cc_new
            WHERE cc_new.company_id = ba.company_id
              AND cc_new.code = (SELECT cc_old.code FROM cost_centers cc_old WHERE cc_old.id = ba.cost_center_id)
              AND cc_new.deleted_at IS NULL
            ORDER BY cc_new.id LIMIT 1
       )
     WHERE cost_center_id IN (SELECT id FROM cost_centers WHERE deleted_at IS NOT NULL)
       AND deleted_at IS NULL;

    -- 2. accounting_accounts.cost_center_id
    UPDATE accounting_accounts aa
       SET cost_center_id = (
           SELECT cc_new.id FROM cost_centers cc_new
            WHERE cc_new.company_id = aa.company_id
              AND cc_new.code = (SELECT cc_old.code FROM cost_centers cc_old WHERE cc_old.id = aa.cost_center_id)
              AND cc_new.deleted_at IS NULL
            ORDER BY cc_new.id LIMIT 1
       )
     WHERE cost_center_id IN (SELECT id FROM cost_centers WHERE deleted_at IS NOT NULL)
       AND deleted_at IS NULL;

    -- 3. cash.cost_center_id
    UPDATE cash c
       SET cost_center_id = (
           SELECT cc_new.id FROM cost_centers cc_new
            WHERE cc_new.company_id = c.company_id
              AND cc_new.code = (SELECT cc_old.code FROM cost_centers cc_old WHERE cc_old.id = c.cost_center_id)
              AND cc_new.deleted_at IS NULL
            ORDER BY cc_new.id LIMIT 1
       )
     WHERE cost_center_id IN (SELECT id FROM cost_centers WHERE deleted_at IS NOT NULL)
       AND deleted_at IS NULL;

    -- 4. journal_entry_lines.cost_center_id (tabla SIN soft delete - filtrar via JE padre)
    UPDATE journal_entry_lines jel
       SET cost_center_id = (
           SELECT cc_new.id FROM cost_centers cc_new
            WHERE cc_new.company_id = jel.company_id
              AND cc_new.code = (SELECT cc_old.code FROM cost_centers cc_old WHERE cc_old.id = jel.cost_center_id)
              AND cc_new.deleted_at IS NULL
            ORDER BY cc_new.id LIMIT 1
       )
     WHERE cost_center_id IN (SELECT id FROM cost_centers WHERE deleted_at IS NOT NULL);
END $$;

-- Si la subquery resulta NULL (sin CC equivalente activo en la empresa),
-- el UPDATE setea NULL en la FK (cumpliendo nullability). Asi nunca
-- queda apuntando a un soft-deleted.
