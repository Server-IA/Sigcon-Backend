CREATE TABLE IF NOT EXISTS third_party_role_catalog (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS third_party_status_catalog (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(30) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS third_parties (
    id BIGSERIAL PRIMARY KEY,
    third_party_code VARCHAR(32) NOT NULL,
    nit VARCHAR(15) NOT NULL,
    dv VARCHAR(2) NOT NULL,
    business_name VARCHAR(255) NOT NULL,
    person_type VARCHAR(16) NOT NULL,
    status_id BIGINT NOT NULL,
    municipality_id BIGINT,
    blocking_reason VARCHAR(500),
    address VARCHAR(255),
    phone VARCHAR(30),
    email VARCHAR(255),
    tax_regime VARCHAR(32),
    fiscal_responsibilities VARCHAR(255),
    withholding_info VARCHAR(255),
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

ALTER TABLE third_parties
ALTER COLUMN person_type TYPE VARCHAR(16)
USING (
    CASE UPPER(person_type::text)
        WHEN '0' THEN 'NATURAL'
        WHEN '1' THEN 'JURIDICA'
        ELSE person_type::text
    END
);

ALTER TABLE third_parties
ALTER COLUMN tax_regime TYPE VARCHAR(32)
USING (
    CASE UPPER(tax_regime::text)
        WHEN '0' THEN 'SIMPLIFIED'
        WHEN '1' THEN 'COMMON'
        ELSE tax_regime::text
    END
);

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
SELECT 'CLIENTE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM third_party_role_catalog WHERE name = 'CLIENTE');

INSERT INTO third_party_role_catalog (name, created_at, updated_at)
SELECT 'PROVEEDOR', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM third_party_role_catalog WHERE name = 'PROVEEDOR');

INSERT INTO third_party_role_catalog (name, created_at, updated_at)
SELECT 'EMPLEADO', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM third_party_role_catalog WHERE name = 'EMPLEADO');

INSERT INTO third_party_role_catalog (name, created_at, updated_at)
SELECT 'ACREEDOR', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM third_party_role_catalog WHERE name = 'ACREEDOR');

INSERT INTO third_party_role_catalog (name, created_at, updated_at)
SELECT 'DEUDOR', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM third_party_role_catalog WHERE name = 'DEUDOR');

INSERT INTO third_party_status_catalog (name, created_at, updated_at)
SELECT 'ACTIVO', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM third_party_status_catalog WHERE name = 'ACTIVO');

INSERT INTO third_party_status_catalog (name, created_at, updated_at)
SELECT 'BLOQUEADO', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM third_party_status_catalog WHERE name = 'BLOQUEADO');

INSERT INTO third_party_status_catalog (name, created_at, updated_at)
SELECT 'INACTIVO', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM third_party_status_catalog WHERE name = 'INACTIVO');

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
    person_type,
    status_id,
    address,
    phone,
    email,
    tax_regime,
    fiscal_responsibilities,
    withholding_info,
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
    'JURIDICA',
    (SELECT id FROM third_party_status_catalog WHERE name = 'ACTIVO'),
    'Calle 100 # 20-30',
    '6011234567',
    'demo1@thirdparty.com',
    'COMMON',
    'R-99-PN',
    'RET_FUENTE',
    50000000,
    '30 dias',
    'CORPORATIVO',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM third_parties WHERE nit = '9001234567' AND dv = '1' AND deleted_at IS NULL
);

INSERT INTO third_parties (
    third_party_code,
    nit,
    dv,
    business_name,
    person_type,
    status_id,
    address,
    phone,
    email,
    tax_regime,
    fiscal_responsibilities,
    withholding_info,
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
    'NATURAL',
    (SELECT id FROM third_party_status_catalog WHERE name = 'ACTIVO'),
    'Carrera 50 # 10-15',
    '6047654321',
    'demo2@thirdparty.com',
    'SIMPLIFIED',
    'R-05',
    'RET_ICA',
    0,
    'CONTADO',
    'PERSONA NATURAL',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM third_parties WHERE nit = '9019876543' AND dv = '5' AND deleted_at IS NULL
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
