-- V33: Agrega el concepto contable AP_COMPRAS_DEFAULT.
--
-- Motivacion: el procesador AAEF de facturas de compra (Type=02) no recibe
-- desde AgroFusion el detalle de cuenta contable por linea. Para cumplir
-- HU-INT-RF-04 E2 ("JE con 2205 CxP / 2408 IVA descontable / 2365 Retenciones")
-- se necesita una cuenta default para el DEBITO de cada factura AAEF.
--
-- PUC 5135 (Servicios) es la cuenta generica de gasto operacional usada como
-- catch-all para compras AAEF cuando AgroFusion no detalla la naturaleza del
-- gasto. El contador puede reclasificar manualmente si corresponde.
--
-- Idempotente: si el concepto ya existe no hace nada.

CREATE OR REPLACE FUNCTION ensure_accounting_account_for_puc(
    p_puc_code VARCHAR,
    p_custom_name VARCHAR,
    p_nature VARCHAR
) RETURNS BIGINT AS $$
DECLARE
    v_puc_id BIGINT;
    v_account_id BIGINT;
    v_currency_id BIGINT;
BEGIN
    SELECT id INTO v_puc_id
    FROM cfg_chart_of_accounts
    WHERE account_code = p_puc_code AND deleted_at IS NULL
    ORDER BY id LIMIT 1;

    IF v_puc_id IS NULL THEN
        RAISE EXCEPTION 'V33: PUC % no existe en cfg_chart_of_accounts', p_puc_code;
    END IF;

    SELECT id INTO v_account_id
    FROM accounting_accounts
    WHERE puc_id = v_puc_id AND deleted_at IS NULL
    ORDER BY id LIMIT 1;

    IF v_account_id IS NOT NULL THEN
        RETURN v_account_id;
    END IF;

    SELECT id INTO v_currency_id
    FROM cfg_currency_types
    WHERE deleted_at IS NULL
    ORDER BY id LIMIT 1;

    INSERT INTO accounting_accounts (
        puc_id, custom_name, currency_type_id, cost_center_id,
        tax_rule_id, nature, status, created_at, updated_at, created_by
    ) VALUES (
        v_puc_id,
        LEFT(p_custom_name, 50),
        v_currency_id,
        NULL, NULL,
        p_nature, 'ACTIVE', NOW(), NOW(), NULL
    ) RETURNING id INTO v_account_id;

    RETURN v_account_id;
END;
$$ LANGUAGE plpgsql;

INSERT INTO account_mappings (concept_code, concept_description, puc_code, accounting_account_id, created_at, updated_at)
SELECT 'AP_COMPRAS_DEFAULT',
       'Cuenta debito default para facturas de compra AAEF (PUC 5135 Servicios)',
       '5135',
       ensure_accounting_account_for_puc('5135', 'Servicios compras AAEF', 'DEBIT'),
       NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM account_mappings
    WHERE concept_code = 'AP_COMPRAS_DEFAULT' AND deleted_at IS NULL
);

DROP FUNCTION IF EXISTS ensure_accounting_account_for_puc(VARCHAR, VARCHAR, VARCHAR);
