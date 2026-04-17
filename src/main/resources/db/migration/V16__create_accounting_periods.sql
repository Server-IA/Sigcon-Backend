-- Tabla de períodos contables para validaciones de período cerrado
-- Usada por: Activos (F-ACT-06-03, ERR-MNT-ACT-01), Depreciación, NIIF

CREATE TABLE IF NOT EXISTS accounting_periods (
    id BIGSERIAL PRIMARY KEY,
    year INTEGER NOT NULL,
    month INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    closed_at TIMESTAMP,
    closed_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(year, month)
);

-- Seed: crear períodos del 2026 como OPEN
INSERT INTO accounting_periods (year, month, status, created_at, updated_at)
SELECT y, m, 'OPEN', NOW(), NOW()
FROM generate_series(2026, 2026) AS y, generate_series(1, 12) AS m
WHERE NOT EXISTS (
    SELECT 1 FROM accounting_periods ap WHERE ap.year = y AND ap.month = m
);
