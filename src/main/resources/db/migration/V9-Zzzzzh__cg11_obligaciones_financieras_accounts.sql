-- =============================================================================
-- V9-Zzzzzh: HU-CG-11 (QA reeval Q3) — auto-provisionar cuenta de Obligaciones
--            financieras (PUC 2105) en TODAS las empresas activas.
--
-- Razon: el reporte QA decia que la seccion de FINANCIACION del Flujo de Efectivo
-- (NIC 7) "queda en cero aunque existan comprobantes". La LOGICA de clasificacion
-- ya funciona (un comprobante cuya contrapartida es clase 21 u obligaciones
-- financieras / clase 3 patrimonio se clasifica como FINANCIACION; validado con
-- smoke 2026-05-26). La causa raiz real es que NINGUNA empresa tenia una cuenta
-- contable de clase 21 (obligaciones financieras), por lo que el contador no podia
-- registrar un prestamo y, sin movimientos de financiacion, la seccion salia en 0.
--
-- Este script promueve el PUC 2105 (Obligaciones financieras - Bancos nacionales)
-- a accounting_accounts por cada empresa activa que aun no la tenga. Con la cuenta
-- disponible, el contador puede registrar prestamos (D 1110 Bancos / C 2105) y la
-- seccion de financiacion del Flujo de Efectivo refleja el movimiento.
--
-- Naturaleza CREDIT (es un pasivo). Idempotente: corre en cada arranque y cubre
-- tambien empresas creadas posteriormente. Mismo patron que V9-Z7 (cuentas PPE).
-- =============================================================================

DO $$
DECLARE
    c RECORD;
    cop_id BIGINT;
BEGIN
    SELECT id INTO cop_id FROM cfg_currency_types
     WHERE iso_code = 'COP' AND deleted_at IS NULL LIMIT 1;
    IF cop_id IS NULL THEN
        RAISE NOTICE 'V9-Zzzzzh skipped: no cfg_currency_types COP found';
        RETURN;
    END IF;

    FOR c IN SELECT id, business_name FROM companies
              WHERE status = 'ACTIVE' AND deleted_at IS NULL ORDER BY id
    LOOP
        INSERT INTO accounting_accounts(company_id, custom_name, nature, status,
                                          currency_type_id, puc_id,
                                          created_at, updated_at)
        SELECT c.id, 'Obligaciones financieras (' || ca.account_code || ')',
               'CREDIT', 'ACTIVE', cop_id, ca.id, NOW(), NOW()
          FROM cfg_chart_of_accounts ca
         WHERE ca.deleted_at IS NULL
           AND ca.account_code = '2105'
           AND NOT EXISTS (
               SELECT 1 FROM accounting_accounts a
                WHERE a.company_id = c.id
                  AND a.puc_id = ca.id
                  AND a.deleted_at IS NULL);

        RAISE NOTICE 'V9-Zzzzzh: cuenta 2105 asegurada en empresa % (id=%)', c.business_name, c.id;
    END LOOP;
END $$;
