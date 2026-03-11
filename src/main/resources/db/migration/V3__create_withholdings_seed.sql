CREATE TABLE IF NOT EXISTS withholdings (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(45) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL
);

INSERT INTO withholdings (name, code, created_at, updated_at)
SELECT 'RETEIVA', 'RETEIVA', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM withholdings WHERE UPPER(code) = 'RETEIVA' AND deleted_at IS NULL);

INSERT INTO withholdings (name, code, created_at, updated_at)
SELECT 'RETEICA', 'RETEICA', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM withholdings WHERE UPPER(code) = 'RETEICA' AND deleted_at IS NULL);

INSERT INTO withholdings (name, code, created_at, updated_at)
SELECT 'RETEFUENTE', 'RETEFUENTE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM withholdings WHERE UPPER(code) = 'RETEFUENTE' AND deleted_at IS NULL);