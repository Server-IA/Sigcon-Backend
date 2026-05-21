-- =====================================================================
-- BNK-HU-072 — Parámetros del motor de matching (global + override por cuenta).
-- Tabla nueva (R4) multi-tenant. Seed global (cuenta_bancaria_id NULL) por empresa.
-- Idempotente.
-- =====================================================================

CREATE TABLE IF NOT EXISTS parametros_matching (
    id                        BIGSERIAL PRIMARY KEY,
    company_id                BIGINT NOT NULL,
    cuenta_bancaria_id        BIGINT,                       -- NULL = parámetros globales de la empresa
    tolerancia_monto_abs      NUMERIC(20,2) NOT NULL DEFAULT 0.01,
    tolerancia_monto_pct      NUMERIC(6,3)  NOT NULL DEFAULT 0,
    tolerancia_fecha_dias     INTEGER       NOT NULL DEFAULT 2,
    umbral_score_auto_aprobar INTEGER       NOT NULL DEFAULT 95,
    umbral_score_sugerir      INTEGER       NOT NULL DEFAULT 60,
    permitir_n_a_m            BOOLEAN       NOT NULL DEFAULT TRUE,
    peso_monto                INTEGER       NOT NULL DEFAULT 50,
    peso_fecha                INTEGER       NOT NULL DEFAULT 30,
    peso_texto                INTEGER       NOT NULL DEFAULT 15,
    peso_referencia           INTEGER       NOT NULL DEFAULT 5,
    created_at                TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_parametros_matching_company ON parametros_matching (company_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_parametros_matching_global
    ON parametros_matching (company_id) WHERE cuenta_bancaria_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_parametros_matching_cuenta
    ON parametros_matching (company_id, cuenta_bancaria_id) WHERE cuenta_bancaria_id IS NOT NULL;

-- Seed global por empresa (BNK-HU-072 E1).
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN SELECT id FROM companies WHERE deleted_at IS NULL LOOP
        -- Hibernate crea la tabla sin los DEFAULT del CREATE TABLE: el seed debe
        -- proveer todas las columnas NOT NULL explicitamente.
        INSERT INTO parametros_matching (company_id, cuenta_bancaria_id, tolerancia_monto_abs,
            tolerancia_monto_pct, tolerancia_fecha_dias, umbral_score_auto_aprobar,
            umbral_score_sugerir, permitir_n_a_m, peso_monto, peso_fecha, peso_texto,
            peso_referencia, created_at, updated_at)
        SELECT c.id, NULL, 0.01, 0, 2, 95, 60, TRUE, 50, 30, 15, 5, NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM parametros_matching pm
            WHERE pm.company_id = c.id AND pm.cuenta_bancaria_id IS NULL
        );
    END LOOP;
END $$;

COMMENT ON TABLE parametros_matching IS 'BNK-HU-072: tolerancias y umbrales del motor de matching (global por empresa y override por cuenta)';
