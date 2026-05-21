-- =====================================================================
-- BNK-HU-076: TRM, conversión dual de moneda extranjera y diferencia en cambio (NIC 21).
-- =====================================================================
-- Las tablas trm_historica y bnk_config_trm las crea Hibernate ddl-auto desde
-- las entidades. Esta migración (aditiva, idempotente):
--   1. Documenta/garantiza las columnas duales en financial_movements (E2).
--   2. Siembra las cuentas PUC 421020 (ingreso) y 530530 (gasto) como
--      accounting_accounts por empresa (HU-076 E6) — mismo patrón que V9-Zzzzv.
--   3. Siembra la política TRM por defecto (FECHA_MOVIMIENTO) por empresa (E8).
--   4. Índices de trm_historica + menús de UI (TRM y Diferencia en cambio).
-- Prefijo V9-Zzzzz (z minúscula) => ordena DESPUES de V9-Z__multi (R: '_'<'z')
-- y de V9-1 (seed PUC), requisito para resolver los códigos PUC.
-- =====================================================================

-- ----- 1) Columnas duales en financial_movements (E2) — idempotente
ALTER TABLE financial_movements ADD COLUMN IF NOT EXISTS monto_funcional NUMERIC(20,2);
ALTER TABLE financial_movements ADD COLUMN IF NOT EXISTS trm_aplicada    NUMERIC(18,6);

-- ----- 2) Cuentas PUC 421020 / 530530 como accounting_accounts por empresa (E6)
INSERT INTO accounting_accounts
    (company_id, puc_id, nature, status, custom_name, currency_type_id, created_at, updated_at)
SELECT c.id,
       coa.id,
       coa.account_nature,
       'ACTIVE',
       coa.account_name,
       (SELECT id FROM cfg_currency_types WHERE iso_code = 'COP' AND deleted_at IS NULL ORDER BY id LIMIT 1),
       NOW(),
       NOW()
FROM companies c
CROSS JOIN cfg_chart_of_accounts coa
WHERE c.deleted_at IS NULL
  AND c.status = 'ACTIVE'
  AND coa.account_code IN ('421020', '530530')
  AND coa.deleted_at IS NULL
  AND NOT EXISTS (
        SELECT 1 FROM accounting_accounts aa
         WHERE aa.company_id = c.id
           AND aa.puc_id = coa.id
           AND aa.deleted_at IS NULL
  );

-- ----- 3) Política TRM por defecto por empresa (E8)
INSERT INTO bnk_config_trm (company_id, politica_trm, created_at, updated_at)
SELECT c.id, 'FECHA_MOVIMIENTO', NOW(), NOW()
FROM companies c
WHERE c.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM bnk_config_trm bc WHERE bc.company_id = c.id);

-- ----- 4) Índices de trm_historica
CREATE UNIQUE INDEX IF NOT EXISTS uk_trm_company_iso_fecha
    ON trm_historica (company_id, currency_iso, fecha) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_trm_iso_fecha ON trm_historica (currency_iso, fecha);

-- ----- 5) Menús de UI (Bancos y Cajas)
DO $$
DECLARE v_bnk BIGINT;
BEGIN
    SELECT id INTO v_bnk FROM modules WHERE name = 'Bancos y Cajas' AND deleted_at IS NULL LIMIT 1;
    IF v_bnk IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, required_permission_code, created_at, updated_at)
        SELECT 'TRM (Moneda extranjera)', 'ri-exchange-dollar-line', 'trm', 23, v_bnk, 'ACTIVE', 'TRM_HISTORICA', true, 'BNK.CUENTAS.VER', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'TRM_HISTORICA' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, required_permission_code, created_at, updated_at)
        SELECT 'Diferencia en cambio', 'ri-funds-line', 'diferencia-cambio', 24, v_bnk, 'ACTIVE', 'DIFERENCIA_CAMBIO', true, 'BNK.CUENTAS.VER', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'DIFERENCIA_CAMBIO' AND deleted_at IS NULL);
    END IF;
END $$;
