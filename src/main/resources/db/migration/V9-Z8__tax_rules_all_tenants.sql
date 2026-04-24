-- =============================================================================
-- V9-Z8: Reglas tributarias (ruler_tax) para todas las empresas activas.
--
-- V9-Z4 cubria AR/AP/BNK/NOM/ACT pero NO sembraba reglas tributarias. Sin
-- ellas, al crear facturas el usuario no puede calcular IVA/retenciones
-- automaticamente.
--
-- Se crean 4 reglas base por empresa (alineadas con normativa colombiana
-- 2026):
--   1. IVA 19% (TAX)
--   2. RTE FUENTE Compras 2.5% (WITHHOLDING, UVT 27)
--   3. RTE FUENTE Servicios 4% (WITHHOLDING, UVT 4)
--   4. RETE ICA Bogota 0.966% (WITHHOLDING)
--
-- Requiere: accounting_accounts de las empresas (V9-Z7 + provision inicial).
-- Idempotente: guard NOT EXISTS por (company_id, name).
-- =============================================================================

DO $$
DECLARE
    c RECORD;
    v_acc_iva BIGINT;
    v_acc_rte_fte BIGINT;
    v_acc_rete_ica BIGINT;
    v_uvt_2026 DOUBLE PRECISION := 47065.0;  -- UVT 2026
BEGIN
    FOR c IN SELECT id, business_name FROM companies
              WHERE status = 'ACTIVE' AND deleted_at IS NULL ORDER BY id
    LOOP
        -- Resolver cuentas contables para IVA/Retenciones en la empresa actual
        -- Buscar por codigo PUC via chart_of_accounts
        SELECT aa.id INTO v_acc_iva
          FROM accounting_accounts aa
          JOIN cfg_chart_of_accounts ca ON ca.id = aa.puc_id
         WHERE aa.company_id = c.id
           AND aa.deleted_at IS NULL
           AND ca.account_code LIKE '2408%'
         ORDER BY ca.account_code LIMIT 1;

        SELECT aa.id INTO v_acc_rte_fte
          FROM accounting_accounts aa
          JOIN cfg_chart_of_accounts ca ON ca.id = aa.puc_id
         WHERE aa.company_id = c.id
           AND aa.deleted_at IS NULL
           AND ca.account_code LIKE '2365%'
         ORDER BY ca.account_code LIMIT 1;

        SELECT aa.id INTO v_acc_rete_ica
          FROM accounting_accounts aa
          JOIN cfg_chart_of_accounts ca ON ca.id = aa.puc_id
         WHERE aa.company_id = c.id
           AND aa.deleted_at IS NULL
           AND ca.account_code LIKE '2368%'
         ORDER BY ca.account_code LIMIT 1;

        -- Fallback: si no hay 2368 usar 2365
        IF v_acc_rete_ica IS NULL THEN v_acc_rete_ica := v_acc_rte_fte; END IF;

        -- 1. IVA 19%
        IF v_acc_iva IS NOT NULL THEN
            INSERT INTO ruler_tax (company_id, name, type_ruler_tax, percentage, description,
                                     scope, status, start_date, end_date,
                                     accounting_account_id, created_at, updated_at)
            SELECT c.id, 'IVA 19%', 'TAX', 19.0,
                   'Impuesto al Valor Agregado - tarifa general (Art. 468 ET)',
                   'VENTA,COMPRA', 'ACTIVE', '2026-01-01'::date, '2026-12-31'::date,
                   v_acc_iva, NOW(), NOW()
             WHERE NOT EXISTS (SELECT 1 FROM ruler_tax
                                WHERE company_id = c.id AND name = 'IVA 19%'
                                  AND deleted_at IS NULL);
        END IF;

        -- 2. RTE FUENTE Compras 2.5% (declarantes, UVT 27)
        IF v_acc_rte_fte IS NOT NULL THEN
            INSERT INTO ruler_tax (company_id, name, type_ruler_tax, percentage, description,
                                     scope, status, start_date, end_date,
                                     min_amount_uvt, uvt_value_year,
                                     accounting_account_id, created_at, updated_at)
            SELECT c.id, 'RTE FTE Compras 2.5%', 'WITHHOLDING', 2.5,
                   'Retencion en la fuente - compras generales declarantes',
                   'COMPRA', 'ACTIVE', '2026-01-01'::date, '2026-12-31'::date,
                   27.0, v_uvt_2026, v_acc_rte_fte, NOW(), NOW()
             WHERE NOT EXISTS (SELECT 1 FROM ruler_tax
                                WHERE company_id = c.id AND name = 'RTE FTE Compras 2.5%'
                                  AND deleted_at IS NULL);

            INSERT INTO ruler_tax (company_id, name, type_ruler_tax, percentage, description,
                                     scope, status, start_date, end_date,
                                     min_amount_uvt, uvt_value_year,
                                     accounting_account_id, created_at, updated_at)
            SELECT c.id, 'RTE FTE Servicios 4%', 'WITHHOLDING', 4.0,
                   'Retencion en la fuente - servicios en general (Art. 392 ET)',
                   'SERVICIO', 'ACTIVE', '2026-01-01'::date, '2026-12-31'::date,
                   4.0, v_uvt_2026, v_acc_rte_fte, NOW(), NOW()
             WHERE NOT EXISTS (SELECT 1 FROM ruler_tax
                                WHERE company_id = c.id AND name = 'RTE FTE Servicios 4%'
                                  AND deleted_at IS NULL);
        END IF;

        -- 3. RETE ICA Bogota 0.966%
        IF v_acc_rete_ica IS NOT NULL THEN
            INSERT INTO ruler_tax (company_id, name, type_ruler_tax, percentage, description,
                                     scope, status, start_date, end_date,
                                     accounting_account_id, created_at, updated_at)
            SELECT c.id, 'RETE ICA Bogota', 'WITHHOLDING', 0.966,
                   'Retencion en la fuente - Impuesto de Industria y Comercio Bogota',
                   'VENTA,SERVICIO', 'ACTIVE', '2026-01-01'::date, '2026-12-31'::date,
                   v_acc_rete_ica, NOW(), NOW()
             WHERE NOT EXISTS (SELECT 1 FROM ruler_tax
                                WHERE company_id = c.id AND name = 'RETE ICA Bogota'
                                  AND deleted_at IS NULL);
        END IF;

        RAISE NOTICE 'V9-Z8: reglas tributarias sembradas para % (id=%)', c.business_name, c.id;
    END LOOP;
END $$;
