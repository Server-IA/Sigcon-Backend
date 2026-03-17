CREATE TABLE IF NOT EXISTS niif_parameters (

    id SERIAL PRIMARY KEY,

    asset_category VARCHAR(100) NOT NULL,

    depreciation_method VARCHAR(50) NOT NULL,

    standard_useful_life INTEGER NOT NULL,

    revaluation_months_limit INTEGER DEFAULT 12,

    requires_impairment BOOLEAN DEFAULT FALSE

);

CREATE TABLE IF NOT EXISTS niif_verifications (

    id SERIAL PRIMARY KEY,

    asset_id BIGINT NOT NULL,

    result VARCHAR(20) NOT NULL,

    message TEXT,

    verification_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP

);

CREATE TABLE IF NOT EXISTS niif_corrections (

    id SERIAL PRIMARY KEY,

    asset_id BIGINT NOT NULL,

    correction_type VARCHAR(50),

    justification TEXT,

    previous_value TEXT,

    new_value TEXT,

    correction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP

);