-- Reseteo
DROP INDEX IF EXISTS uk_third_parties_nit_dv_active;
DROP INDEX IF EXISTS uk_third_parties_code_active;

DROP INDEX IF EXISTS uk_third_party_withholding_assignment_active;

DROP INDEX IF EXISTS uk_payment_terms_name_active;
DROP INDEX IF EXISTS uk_payment_terms_days_active;

DROP INDEX IF EXISTS uk_commercial_data_third_party_active;

-- Terceros
CREATE UNIQUE INDEX IF NOT EXISTS uk_third_parties_nit_dv_active
ON third_parties (nit, dv)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_third_parties_code_active
ON third_parties (third_party_code)
WHERE deleted_at IS NULL;

-- Terceros impuestos
CREATE UNIQUE INDEX IF NOT EXISTS uk_third_party_withholding_assignment_active
ON third_party_withholding_assignments (third_party_id, withholding_id)
WHERE deleted_at IS NULL;

-- Terceros terminos de pago
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_terms_name_active
ON payment_terms (name)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_terms_days_active
ON payment_terms (days)
WHERE deleted_at IS NULL;

--Datos Comerciales (Commercial_Data), indexes para validar la unicidad 

CREATE UNIQUE INDEX IF NOT EXISTS uk_commercial_data_third_party_active
ON commercial_data (client_id)
WHERE deleted_at IS NULL; 