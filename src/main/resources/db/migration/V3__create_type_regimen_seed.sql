CREATE TABLE IF NOT EXISTS type_regimen (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(45) NOT NULL,
    code VARCHAR(45) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL
);

INSERT INTO type_regimen (name, code, created_at, updated_at)
SELECT 'NO RESPONSABLE DE IVA', 'NO_RESPONSABLE_IVA', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM type_regimen WHERE UPPER(code) = 'NO_RESPONSABLE_IVA' AND deleted_at IS NULL);

INSERT INTO type_regimen (name, code, created_at, updated_at)
SELECT 'RESPONSABLE DE IVA', 'RESPONSABLE_IVA', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM type_regimen WHERE UPPER(code) = 'RESPONSABLE_IVA' AND deleted_at IS NULL);
