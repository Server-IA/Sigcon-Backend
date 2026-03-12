-- Index para validar unicidad

CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_terms_name_active
ON payment_terms (name)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_terms_days_active
ON payment_terms (days)
WHERE deleted_at IS NULL;

ALTER TABLE payment_terms
ALTER COLUMN created_at SET DEFAULT NOW();

ALTER TABLE payment_terms
ALTER COLUMN updated_at SET DEFAULT NOW();

-- Insertar términos de pago
INSERT INTO payment_terms (name, days)
SELECT 'Diario', 1
WHERE NOT EXISTS (
    SELECT 1 FROM payment_terms 
    WHERE name = 'Diario' AND deleted_at IS NULL
);

INSERT INTO payment_terms (name, days)
SELECT 'Semanal', 7
WHERE NOT EXISTS (
    SELECT 1 FROM payment_terms 
    WHERE name = 'Semanal' AND deleted_at IS NULL
);

INSERT INTO payment_terms (name, days)
SELECT 'Quincenal', 15
WHERE NOT EXISTS (
    SELECT 1 FROM payment_terms 
    WHERE name = 'Quincenal' AND deleted_at IS NULL
);

INSERT INTO payment_terms (name, days)
SELECT 'Mensual', 30
WHERE NOT EXISTS (
    SELECT 1 FROM payment_terms 
    WHERE name = 'Mensual' AND deleted_at IS NULL
);

INSERT INTO payment_terms (name, days)
SELECT 'Trimestral', 90
WHERE NOT EXISTS (
    SELECT 1 FROM payment_terms 
    WHERE name = 'Trimestral' AND deleted_at IS NULL
);

INSERT INTO payment_terms (name, days)
SELECT 'Semestral', 180
WHERE NOT EXISTS (
    SELECT 1 FROM payment_terms 
    WHERE name = 'Semestral' AND deleted_at IS NULL
);

INSERT INTO payment_terms (name, days)
SELECT 'Anual', 360
WHERE NOT EXISTS (
    SELECT 1 FROM payment_terms 
    WHERE name = 'Anual' AND deleted_at IS NULL
);