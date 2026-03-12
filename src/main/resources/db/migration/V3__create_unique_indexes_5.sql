CREATE UNIQUE INDEX IF NOT EXISTS uk_type_regimen_code_active
ON type_regimen (code)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_type_organization_code_active
ON type_organization (code)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_withholdings_code_active
ON withholdings (code)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_third_contact_third_party_id
ON third_contact (third_party_id)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_third_party_withholding_assignment_active
ON third_party_withholding_assignments (third_party_id, withholding_id)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_third_party_withholding_assignments_third_party_id
ON third_party_withholding_assignments (third_party_id)
WHERE deleted_at IS NULL;
