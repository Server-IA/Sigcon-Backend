CREATE UNIQUE INDEX IF NOT EXISTS uk_countries_code_active
ON countries (code)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_countries_name_active
ON countries (name)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_municipalities_country_name_active
ON municipalities (country_id, name)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_municipalities_code_active
ON municipalities (code)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_third_parties_municipality_id
ON third_parties (municipality_id)
WHERE deleted_at IS NULL;
