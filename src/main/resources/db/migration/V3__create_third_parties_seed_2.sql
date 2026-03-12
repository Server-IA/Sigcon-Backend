CREATE TABLE IF NOT EXISTS third_party_role_catalog (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS third_party_status_catalog (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(30) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS third_parties (
    id BIGSERIAL PRIMARY KEY,
    third_party_code VARCHAR(32) NOT NULL,
    nit VARCHAR(15) NOT NULL,
    dv VARCHAR(2) NOT NULL,
    business_name VARCHAR(255) NOT NULL,
    status_id BIGINT NOT NULL,
    municipality_id BIGINT,
    blocking_reason VARCHAR(500),
    credit_limit NUMERIC(19, 2),
    payment_terms VARCHAR(100),
    market_segment VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_third_parties_status_id
        FOREIGN KEY (status_id) REFERENCES third_party_status_catalog(id)
);

ALTER TABLE third_parties
ADD COLUMN IF NOT EXISTS status_id BIGINT;

ALTER TABLE third_parties
ADD COLUMN IF NOT EXISTS municipality_id BIGINT;

ALTER TABLE third_parties
ADD COLUMN IF NOT EXISTS blocking_reason VARCHAR(500);

CREATE TABLE IF NOT EXISTS third_party_role_assignments (
    third_party_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (third_party_id, role_id),
    CONSTRAINT fk_third_party_role_assignments_third_party
        FOREIGN KEY (third_party_id) REFERENCES third_parties(id),
    CONSTRAINT fk_third_party_role_assignments_role
        FOREIGN KEY (role_id) REFERENCES third_party_role_catalog(id)
);

INSERT INTO third_party_role_catalog (name, created_at, updated_at)
SELECT v.name, NOW(), NOW()
FROM (VALUES
    ('CLIENTE'),
    ('PROVEEDOR'),
    ('EMPLEADO'),
    ('ACREEDOR'),
    ('DEUDOR')
) AS v(name)
WHERE NOT EXISTS (
    SELECT 1
    FROM third_party_role_catalog rc
    WHERE UPPER(TRIM(rc.name)) = UPPER(TRIM(v.name))
);

INSERT INTO third_party_status_catalog (name, created_at, updated_at)
SELECT v.name, NOW(), NOW()
FROM (VALUES
    ('ACTIVO'),
    ('BLOQUEADO'),
    ('INACTIVO')
) AS v(name)
WHERE NOT EXISTS (
    SELECT 1
    FROM third_party_status_catalog sc
    WHERE UPPER(TRIM(sc.name)) = UPPER(TRIM(v.name))
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'third_parties'
          AND column_name = 'status'
    ) THEN
        UPDATE third_parties tp
        SET status_id = (
            SELECT sc.id
            FROM third_party_status_catalog sc
            WHERE sc.name = CASE UPPER(tp.status::text)
                WHEN 'ACTIVE' THEN 'ACTIVO'
                WHEN 'INACTIVE' THEN 'INACTIVO'
                WHEN 'BLOCKED' THEN 'BLOQUEADO'
                WHEN 'ACTIVO' THEN 'ACTIVO'
                WHEN 'INACTIVO' THEN 'INACTIVO'
                WHEN 'BLOQUEADO' THEN 'BLOQUEADO'
                ELSE 'ACTIVO'
            END
        )
        WHERE tp.status_id IS NULL;
    END IF;
END $$;

UPDATE third_parties
SET status_id = (SELECT id FROM third_party_status_catalog WHERE name = 'ACTIVO')
WHERE status_id IS NULL;

INSERT INTO third_parties (
    third_party_code,
    nit,
    dv,
    business_name,
    status_id,
    credit_limit,
    payment_terms,
    market_segment,
    created_at,
    updated_at
)
SELECT
    'TER2026000001',
    '9001234567',
    '1',
    'TERCERO DEMO CLIENTE SAS',
    (SELECT id FROM third_party_status_catalog WHERE name = 'ACTIVO'),
    50000000,
    '30 dias',
    'CORPORATIVO',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM third_parties tp
    WHERE tp.deleted_at IS NULL
      AND (
          (tp.nit = '9001234567' AND tp.dv = '1')
          OR tp.third_party_code = 'TER2026000001'
      )
);

INSERT INTO third_parties (
    third_party_code,
    nit,
    dv,
    business_name,
    status_id,
    credit_limit,
    payment_terms,
    market_segment,
    created_at,
    updated_at
)
SELECT
    'TER2026000002',
    '9019876543',
    '5',
    'TERCERO DEMO EMPLEADO',
    (SELECT id FROM third_party_status_catalog WHERE name = 'ACTIVO'),
    0,
    'CONTADO',
    'PERSONA NATURAL',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM third_parties tp
    WHERE tp.deleted_at IS NULL
      AND (
          (tp.nit = '9019876543' AND tp.dv = '5')
          OR tp.third_party_code = 'TER2026000002'
      )
);

INSERT INTO third_party_role_assignments (third_party_id, role_id)
SELECT tp.id, rc.id
FROM third_parties tp
JOIN third_party_role_catalog rc ON rc.name = 'CLIENTE'
WHERE tp.nit = '9001234567'
  AND NOT EXISTS (
      SELECT 1 FROM third_party_role_assignments a
      WHERE a.third_party_id = tp.id AND a.role_id = rc.id
  );

INSERT INTO third_party_role_assignments (third_party_id, role_id)
SELECT tp.id, rc.id
FROM third_parties tp
JOIN third_party_role_catalog rc ON rc.name = 'PROVEEDOR'
WHERE tp.nit = '9001234567'
  AND NOT EXISTS (
      SELECT 1 FROM third_party_role_assignments a
      WHERE a.third_party_id = tp.id AND a.role_id = rc.id
  );

INSERT INTO third_party_role_assignments (third_party_id, role_id)
SELECT tp.id, rc.id
FROM third_parties tp
JOIN third_party_role_catalog rc ON rc.name = 'EMPLEADO'
WHERE tp.nit = '9019876543'
  AND NOT EXISTS (
      SELECT 1 FROM third_party_role_assignments a
      WHERE a.third_party_id = tp.id AND a.role_id = rc.id
  );
