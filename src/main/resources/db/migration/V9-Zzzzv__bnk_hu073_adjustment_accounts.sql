-- =====================================================================
-- BNK-HU-073: cuentas contables para asientos de ajuste de conciliacion
-- =====================================================================
-- HU-073 E1 mapea el tipo de movimiento a cuentas PUC contrapartida:
--   GMF            -> DB 530525 / CR cuenta_bancaria
--   COMISION       -> DB 530505 / CR cuenta_bancaria
--   INTERES_GANADO -> DB cuenta_bancaria / CR 421005
--   INTERES_PAGADO -> DB 530520 / CR cuenta_bancaria
-- Estas cuentas viven en el catalogo PUC (cfg_chart_of_accounts) pero NO
-- existian como accounting_accounts operativas por empresa. Esta migracion
-- las crea para CADA empresa activa de forma idempotente. Re-ejecutable en
-- cada arranque (DataInitializer corre todos los scripts ordenados); el
-- WHERE NOT EXISTS evita duplicados.
--
-- Nombre lexical V9-Zzzz* (z minuscula) para ordenar DESPUES de V9-Z__multi
-- (R: '_' 0x5F < 'z' 0x7A) y que _tenant_auto_provision ya haya corrido.
-- =====================================================================

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
  AND coa.account_code IN ('530525', '530505', '530520', '421005')
  AND coa.deleted_at IS NULL
  AND NOT EXISTS (
        SELECT 1 FROM accounting_accounts aa
         WHERE aa.company_id = c.id
           AND aa.puc_id = coa.id
           AND aa.deleted_at IS NULL
  );
