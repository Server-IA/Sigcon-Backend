ALTER TABLE third_parties
ADD COLUMN IF NOT EXISTS municipality_id BIGINT;

ALTER TABLE third_parties
ADD COLUMN IF NOT EXISTS blocking_reason VARCHAR(500);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'third_parties'
          AND column_name = 'city'
    ) THEN
        UPDATE third_parties tp
        SET municipality_id = m.id
        FROM municipalities m
        WHERE tp.municipality_id IS NULL
          AND UPPER(COALESCE(tp.city, '')) = UPPER(m.name)
          AND m.deleted_at IS NULL;
    END IF;
END $$;

UPDATE third_parties tp
SET municipality_id = (
    SELECT m.id
    FROM municipalities m
    WHERE UPPER(m.code) = '11001'
      AND m.deleted_at IS NULL
    LIMIT 1
)
WHERE tp.municipality_id IS NULL;

UPDATE third_parties tp
SET municipality_id = (
    SELECT m.id FROM municipalities m
    WHERE UPPER(m.code) = '11001'
      AND m.deleted_at IS NULL
    LIMIT 1
)
WHERE tp.nit = '9001234567'
  AND tp.dv = '1';

UPDATE third_parties tp
SET municipality_id = (
    SELECT m.id FROM municipalities m
    WHERE UPPER(m.code) = '05001'
      AND m.deleted_at IS NULL
    LIMIT 1
)
WHERE tp.nit = '9019876543'
  AND tp.dv = '5';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_third_parties_municipality_id'
          AND table_name = 'third_parties'
    ) THEN
        ALTER TABLE third_parties
        ADD CONSTRAINT fk_third_parties_municipality_id
            FOREIGN KEY (municipality_id) REFERENCES municipalities(id);
    END IF;
END $$;

ALTER TABLE third_parties DROP COLUMN IF EXISTS city;
ALTER TABLE third_parties DROP COLUMN IF EXISTS department;
ALTER TABLE third_parties DROP COLUMN IF EXISTS country;
