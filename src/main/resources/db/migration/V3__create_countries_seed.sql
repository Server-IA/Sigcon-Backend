CREATE TABLE IF NOT EXISTS countries (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(3) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL
);

INSERT INTO countries (name, code, created_at, updated_at)
SELECT 'COLOMBIA', 'COL', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM countries WHERE UPPER(code) = 'COL' AND deleted_at IS NULL);

INSERT INTO countries (name, code, created_at, updated_at)
SELECT 'ESTADOS UNIDOS', 'USA', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM countries WHERE UPPER(code) = 'USA' AND deleted_at IS NULL);

INSERT INTO countries (name, code, created_at, updated_at)
SELECT 'MEXICO', 'MEX', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM countries WHERE UPPER(code) = 'MEX' AND deleted_at IS NULL);