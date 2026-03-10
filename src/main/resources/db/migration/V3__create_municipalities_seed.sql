CREATE TABLE IF NOT EXISTS municipalities (
    id BIGSERIAL PRIMARY KEY,
    country_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(45) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_municipalities_country_id
        FOREIGN KEY (country_id) REFERENCES countries(id)
);

INSERT INTO municipalities (country_id, name, code, created_at, updated_at)
SELECT c.id, 'BOGOTA', '11001', NOW(), NOW()
FROM countries c
WHERE UPPER(c.code) = 'COL'
  AND NOT EXISTS (
      SELECT 1 FROM municipalities m
      WHERE UPPER(m.code) = '11001' AND m.deleted_at IS NULL
  );

INSERT INTO municipalities (country_id, name, code, created_at, updated_at)
SELECT c.id, 'MEDELLIN', '05001', NOW(), NOW()
FROM countries c
WHERE UPPER(c.code) = 'COL'
  AND NOT EXISTS (
      SELECT 1 FROM municipalities m
      WHERE UPPER(m.code) = '05001' AND m.deleted_at IS NULL
  );

INSERT INTO municipalities (country_id, name, code, created_at, updated_at)
SELECT c.id, 'CALI', '76001', NOW(), NOW()
FROM countries c
WHERE UPPER(c.code) = 'COL'
  AND NOT EXISTS (
      SELECT 1 FROM municipalities m
      WHERE UPPER(m.code) = '76001' AND m.deleted_at IS NULL
  );