-- V9-ZZZR — Hotfix arranque Dokploy 2026-05-07.
-- El backend crasheaba con `duplicate key value violates unique constraint
-- "uk_accounting_account_company_name_active"` al re-ejecutar el bootstrap
-- multi-tenant V9-Z (`_tenant_auto_provision` -> `_ensure_tenant_account_mapping`).
--
-- Causa: la funcion buscaba la accounting_account por (company_id, puc_id).
-- Si la fila ya existia para la empresa pero con `puc_id` distinto o NULL
-- (residuo de seeds legacy o de la propagacion V10-D que asigno company_id
-- sin alinear puc_id), el SELECT no encontraba match y el INSERT chocaba
-- con el UNIQUE parcial sobre (company_id, custom_name).
--
-- Fix: 3 capas defensivas:
--   1) SELECT primero por (company_id, puc_id) (path feliz).
--   2) Si null, SELECT fallback por (company_id, custom_name) — alinea puc_id
--      faltante en filas legacy.
--   3) Si null, INSERT con ON CONFLICT DO NOTHING + RETURNING; si la insercion
--      no produce id (race), hace SELECT final por nombre.
--
-- Idempotente: CREATE OR REPLACE no toca otras filas. Si V9-ZZZR corre tras
-- V10-D arregla la funcion sin reescribir datos historicos.

CREATE OR REPLACE FUNCTION _ensure_tenant_account_mapping(
    p_company_id BIGINT, p_concept_code TEXT, p_puc_code TEXT,
    p_custom_name TEXT, p_nature TEXT
) RETURNS void AS $$
DECLARE
    v_puc_id BIGINT;
    v_acct_id BIGINT;
    v_currency_id BIGINT;
    v_full_name TEXT;
BEGIN
    -- 1. Buscar PUC en cfg_chart_of_accounts (tabla global)
    SELECT id INTO v_puc_id FROM cfg_chart_of_accounts
     WHERE account_code = p_puc_code AND deleted_at IS NULL LIMIT 1;
    IF v_puc_id IS NULL THEN
        RAISE NOTICE 'PUC % no existe en cfg_chart_of_accounts, skip mapping %', p_puc_code, p_concept_code;
        RETURN;
    END IF;

    -- 2. Moneda default (COP = id 1 usualmente)
    SELECT id INTO v_currency_id FROM cfg_currency_types WHERE iso_code = 'COP' AND deleted_at IS NULL LIMIT 1;
    IF v_currency_id IS NULL THEN
        SELECT id INTO v_currency_id FROM cfg_currency_types WHERE deleted_at IS NULL LIMIT 1;
    END IF;

    -- Nombre canonico: 'Clientes (1305)' — alineado con V10-D para preservar
    -- el UNIQUE parcial cuando varios conceptos apuntan al mismo PUC.
    v_full_name := p_custom_name || ' (' || p_puc_code || ')';

    -- 3a. Path feliz: existe accounting_account del tenant para ese PUC.
    SELECT id INTO v_acct_id FROM accounting_accounts
     WHERE company_id = p_company_id AND puc_id = v_puc_id AND deleted_at IS NULL
     LIMIT 1;

    -- 3b. Fallback por nombre. Si la fila existe pero con puc_id distinto o NULL
    -- (legacy), reutilizamos esa fila y opcionalmente alineamos puc_id.
    IF v_acct_id IS NULL THEN
        SELECT id INTO v_acct_id FROM accounting_accounts
         WHERE company_id = p_company_id AND custom_name = v_full_name AND deleted_at IS NULL
         LIMIT 1;
        IF v_acct_id IS NOT NULL THEN
            UPDATE accounting_accounts
               SET puc_id = COALESCE(puc_id, v_puc_id),
                   currency_type_id = COALESCE(currency_type_id, v_currency_id),
                   nature = COALESCE(nature, p_nature),
                   status = COALESCE(status, 'ACTIVE'),
                   updated_at = NOW()
             WHERE id = v_acct_id;
        END IF;
    END IF;

    -- 3c. Si nada existe, INSERT defensivo con catch del UNIQUE compuesto.
    IF v_acct_id IS NULL THEN
        BEGIN
            INSERT INTO accounting_accounts (
                company_id, puc_id, custom_name, currency_type_id, nature, status,
                created_at, updated_at
            )
            VALUES (
                p_company_id, v_puc_id, v_full_name,
                v_currency_id, p_nature, 'ACTIVE',
                NOW(), NOW()
            )
            RETURNING id INTO v_acct_id;
        EXCEPTION WHEN unique_violation THEN
            -- Otra rama del bootstrap inserto la fila entre el SELECT y el INSERT.
            -- Recuperamos el id por nombre (la unicidad garantiza una sola fila).
            SELECT id INTO v_acct_id FROM accounting_accounts
             WHERE company_id = p_company_id AND custom_name = v_full_name AND deleted_at IS NULL
             LIMIT 1;
        END;
    END IF;

    -- 4. Upsert mapping (UNIQUE(company_id, concept_code))
    IF v_acct_id IS NOT NULL THEN
        INSERT INTO account_mappings (
            company_id, concept_code, accounting_account_id, concept_description, puc_code,
            created_at, updated_at
        ) VALUES (
            p_company_id, p_concept_code, v_acct_id,
            'Auto-provisioned (V9-ZZZR hotfix)', p_puc_code,
            NOW(), NOW()
        )
        ON CONFLICT ON CONSTRAINT uk_account_mappings_company_concept DO NOTHING;
    END IF;
END;
$$ LANGUAGE plpgsql;
