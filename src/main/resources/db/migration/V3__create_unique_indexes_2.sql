-- Terceros: V10-D reemplaza ambos UNIQUE por versiones compuestas con company_id.
-- Neutralizado aqui para no recrear los constraints globales tras V10-D.
-- CREATE UNIQUE INDEX IF NOT EXISTS uk_third_parties_nit_dv_active
-- ON third_parties (nit, dv)
-- WHERE deleted_at IS NULL;
--
-- CREATE UNIQUE INDEX IF NOT EXISTS uk_third_parties_code_active
-- ON third_parties (third_party_code)
-- WHERE deleted_at IS NULL;
SELECT 1;  -- placeholder

