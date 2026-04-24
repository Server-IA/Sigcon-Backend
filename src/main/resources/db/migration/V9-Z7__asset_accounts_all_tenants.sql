-- =============================================================================
-- V9-Z7: Promover cuentas PPE del PUC a accounting_accounts en TODAS las
--        empresas activas.
--
-- Razon: V9-M solo creo las cuentas de activos fijos (1504, 1516, 1520, 1524,
-- 1528, 1532, 1540, 1560) en la empresa default (company_id=1). Usuarios de
-- otras empresas no pueden crear reglas de depreciacion porque el formulario
-- no tiene cuentas que seleccionar.
--
-- Este script ejecuta el mismo seed por cada empresa activa, solo si no
-- existe aun para esa empresa. Idempotente.
-- =============================================================================

DO $$
DECLARE
    c RECORD;
    cop_id BIGINT;
BEGIN
    SELECT id INTO cop_id FROM cfg_currency_types
     WHERE iso_code = 'COP' AND deleted_at IS NULL LIMIT 1;
    IF cop_id IS NULL THEN
        RAISE NOTICE 'V9-Z7 skipped: no cfg_currency_types COP found';
        RETURN;
    END IF;

    FOR c IN SELECT id, business_name FROM companies
              WHERE status = 'ACTIVE' AND deleted_at IS NULL ORDER BY id
    LOOP
        INSERT INTO accounting_accounts(company_id, custom_name, nature, status,
                                          currency_type_id, puc_id,
                                          created_at, updated_at)
        SELECT c.id, ca.account_name || ' (' || ca.account_code || ')',
               'DEBIT', 'ACTIVE', cop_id, ca.id, NOW(), NOW()
          FROM cfg_chart_of_accounts ca
         WHERE ca.deleted_at IS NULL
           AND ca.account_code IN ('1504','1516','1520','1524','1528','1532','1540','1560')
           AND NOT EXISTS (
               SELECT 1 FROM accounting_accounts a
                WHERE a.company_id = c.id
                  AND a.puc_id = ca.id
                  AND a.deleted_at IS NULL);

        RAISE NOTICE 'V9-Z7: cuentas PPE seedeadas en empresa % (id=%)', c.business_name, c.id;
    END LOOP;
END $$;
