CREATE TABLE IF NOT EXISTS type_regimen (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(45) NOT NULL,
    code VARCHAR(45) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL
);

INSERT INTO type_regimen (name, code, created_at, updated_at)
SELECT 'PERSONA NATURAL', 'NATURAL', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM type_regimen WHERE UPPER(code) = 'NATURAL' AND deleted_at IS NULL);

INSERT INTO type_regimen (name, code, created_at, updated_at)
SELECT 'PERSONA JURIDICA', 'JURIDICA', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM type_regimen WHERE UPPER(code) = 'JURIDICA' AND deleted_at IS NULL);
