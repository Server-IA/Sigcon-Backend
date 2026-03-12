-- Terceros
CREATE UNIQUE INDEX IF NOT EXISTS uk_third_parties_nit_dv_active
ON third_parties (nit, dv)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_third_parties_code_active
ON third_parties (third_party_code)
WHERE deleted_at IS NULL;

