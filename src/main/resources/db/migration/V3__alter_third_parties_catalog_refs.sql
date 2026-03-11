ALTER TABLE third_party_role_catalog
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

ALTER TABLE third_party_status_catalog
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

ALTER TABLE third_parties
ADD COLUMN IF NOT EXISTS type_organization_id BIGINT;

ALTER TABLE third_parties
ADD COLUMN IF NOT EXISTS type_regimen_id BIGINT;

ALTER TABLE third_parties
ADD COLUMN IF NOT EXISTS withholding_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_third_parties_type_organization_id'
          AND table_name = 'third_parties'
    ) THEN
        ALTER TABLE third_parties
        ADD CONSTRAINT fk_third_parties_type_organization_id
        FOREIGN KEY (type_organization_id) REFERENCES type_organization(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_third_parties_type_regimen_id'
          AND table_name = 'third_parties'
    ) THEN
        ALTER TABLE third_parties
        ADD CONSTRAINT fk_third_parties_type_regimen_id
        FOREIGN KEY (type_regimen_id) REFERENCES type_regimen(id);
    END IF;

END $$;

CREATE TABLE IF NOT EXISTS third_party_withholding_assignments (
    id BIGSERIAL PRIMARY KEY,
    third_party_id BIGINT NOT NULL,
    withholding_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_third_party_withholding_assignments_third_party_id
        FOREIGN KEY (third_party_id) REFERENCES third_parties(id),
    CONSTRAINT fk_third_party_withholding_assignments_withholding_id
        FOREIGN KEY (withholding_id) REFERENCES withholdings(id)
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'third_parties'
          AND column_name = 'tax_regime'
    ) THEN
        UPDATE third_parties tp
        SET type_organization_id = (
            SELECT id FROM type_organization WHERE UPPER(code) =
                CASE UPPER(COALESCE(tp.tax_regime::text, 'COMMON'))
                    WHEN 'SIMPLIFIED' THEN 'NO_RESPONSABLE_IVA'
                    ELSE 'RESPONSABLE_IVA'
                END
            LIMIT 1
        )
        WHERE tp.type_organization_id IS NULL;
    ELSE
        UPDATE third_parties tp
        SET type_organization_id = (
            SELECT id FROM type_organization WHERE UPPER(code) = 'RESPONSABLE_IVA' LIMIT 1
        )
        WHERE tp.type_organization_id IS NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'third_parties'
          AND column_name = 'person_type'
    ) THEN
        UPDATE third_parties tp
        SET type_regimen_id = (
            SELECT id FROM type_regimen WHERE UPPER(code) =
                CASE UPPER(COALESCE(tp.person_type::text, 'JURIDICA'))
                    WHEN 'NATURAL' THEN 'NATURAL'
                    ELSE 'JURIDICA'
                END
            LIMIT 1
        )
        WHERE tp.type_regimen_id IS NULL;
    ELSE
        UPDATE third_parties tp
        SET type_regimen_id = (
            SELECT id FROM type_regimen WHERE UPPER(code) = 'JURIDICA' LIMIT 1
        )
        WHERE tp.type_regimen_id IS NULL;
    END IF;
END $$;

UPDATE third_parties tp
SET withholding_id = (
    SELECT id FROM withholdings WHERE UPPER(code) = 'RETEFUENTE' LIMIT 1
)
WHERE tp.withholding_id IS NULL;

INSERT INTO third_party_withholding_assignments (third_party_id, withholding_id, created_at, updated_at)
SELECT tp.id, tp.withholding_id, NOW(), NOW()
FROM third_parties tp
WHERE tp.withholding_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM third_party_withholding_assignments twa
      WHERE twa.third_party_id = tp.id
        AND twa.withholding_id = tp.withholding_id
        AND twa.deleted_at IS NULL
  );

INSERT INTO third_party_withholding_assignments (third_party_id, withholding_id, created_at, updated_at)
SELECT tp.id, w.id, NOW(), NOW()
FROM third_parties tp
JOIN withholdings w ON UPPER(w.code) = 'RETEICA'
WHERE tp.nit = '9019876543'
  AND tp.dv = '5'
  AND NOT EXISTS (
      SELECT 1
      FROM third_party_withholding_assignments twa
      WHERE twa.third_party_id = tp.id
        AND twa.withholding_id = w.id
        AND twa.deleted_at IS NULL
  );

UPDATE third_parties tp
SET type_organization_id = (
    SELECT id FROM type_organization WHERE UPPER(code) = 'RESPONSABLE_IVA' LIMIT 1
)
WHERE tp.nit = '9001234567'
  AND tp.dv = '1';

UPDATE third_parties tp
SET type_regimen_id = (
    SELECT id FROM type_regimen WHERE UPPER(code) = 'JURIDICA' LIMIT 1
)
WHERE tp.nit = '9001234567'
  AND tp.dv = '1';

UPDATE third_parties tp
SET type_organization_id = (
    SELECT id FROM type_organization WHERE UPPER(code) = 'NO_RESPONSABLE_IVA' LIMIT 1
)
WHERE tp.nit = '9019876543'
  AND tp.dv = '5';

UPDATE third_parties tp
SET type_regimen_id = (
    SELECT id FROM type_regimen WHERE UPPER(code) = 'NATURAL' LIMIT 1
)
WHERE tp.nit = '9019876543'
  AND tp.dv = '5';

ALTER TABLE third_parties DROP COLUMN IF EXISTS person_type;
ALTER TABLE third_parties DROP COLUMN IF EXISTS tax_regime;
ALTER TABLE third_parties DROP COLUMN IF EXISTS withholding_id;
ALTER TABLE third_parties DROP COLUMN IF EXISTS fiscal_responsibilities;
ALTER TABLE third_parties DROP COLUMN IF EXISTS withholding_info;
ALTER TABLE third_parties DROP COLUMN IF EXISTS address;
ALTER TABLE third_parties DROP COLUMN IF EXISTS phone;
ALTER TABLE third_parties DROP COLUMN IF EXISTS email;
