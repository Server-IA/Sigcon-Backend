-- =====================================================================
-- BNK-HU-001 (ampliacion) — E5/E6/E8
-- Configuracion de GMF (4x1000, art. 870 ET) y equivalente de efectivo
-- (NIC 7) en cuentas bancarias. Columnas ADITIVAS (regla R4: solo
-- ALTER TABLE ADD COLUMN). No se renombra ni borra nada existente.
-- Idempotente: corre en cada arranque via DataInitializer.
-- =====================================================================

ALTER TABLE bank_accounts ADD COLUMN IF NOT EXISTS aplica_gmf BOOLEAN DEFAULT FALSE;
ALTER TABLE bank_accounts ADD COLUMN IF NOT EXISTS cuenta_gmf_puc_id BIGINT;
ALTER TABLE bank_accounts ADD COLUMN IF NOT EXISTS es_equivalente_efectivo BOOLEAN DEFAULT TRUE;

-- Backfill defensivo: si Hibernate ddl-auto creo la columna sin default,
-- las filas existentes quedan NULL. Aqui se normalizan antes del SET NOT NULL.
UPDATE bank_accounts SET aplica_gmf = FALSE WHERE aplica_gmf IS NULL;
UPDATE bank_accounts SET es_equivalente_efectivo = TRUE WHERE es_equivalente_efectivo IS NULL;

ALTER TABLE bank_accounts ALTER COLUMN aplica_gmf SET DEFAULT FALSE;
ALTER TABLE bank_accounts ALTER COLUMN aplica_gmf SET NOT NULL;
ALTER TABLE bank_accounts ALTER COLUMN es_equivalente_efectivo SET DEFAULT TRUE;
ALTER TABLE bank_accounts ALTER COLUMN es_equivalente_efectivo SET NOT NULL;

COMMENT ON COLUMN bank_accounts.aplica_gmf IS 'BNK-HU-001 E5: la cuenta esta sujeta al GMF 4x1000 (art. 870 ET)';
COMMENT ON COLUMN bank_accounts.cuenta_gmf_puc_id IS 'BNK-HU-001 E5: accounting_accounts.id usada para contabilizar el GMF (PUC tipico 530525). Mapeo R5: el nombre conserva "puc_id" del documento pero referencia la cuenta operativa (accounting_accounts) mapeada al PUC, no chart_of_accounts directamente.';
COMMENT ON COLUMN bank_accounts.es_equivalente_efectivo IS 'BNK-HU-001 E6: equivalente de efectivo segun NIC 7 (default TRUE)';
