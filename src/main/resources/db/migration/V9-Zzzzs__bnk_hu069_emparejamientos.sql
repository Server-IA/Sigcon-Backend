-- =====================================================================
-- BNK-HU-069 / BNK-HU-070 — Motor de matching de conciliación bancaria.
--   * estado_conciliacion en financial_movements (NO_CONCILIADO por defecto).
--   * Tabla emparejamientos (cabecera del match con score/método/estado).
--   * Tabla emparejamiento_detalle (filas N:M por lado EXTRACTO/LIBROS).
-- ADITIVO (R2/R3), idempotente. Multi-tenant (company_id).
--
-- Mapeo de modelo (R5): "movimientos_extracto" = financial_movements con
-- source_type = BANK_IMPORT; "movimientos_libros" = financial_movements con
-- source_type = MANUAL de la misma cuenta. El estado "PENDIENTE" del documento
-- se representa con NO_CONCILIADO (un solo enum para ambos lados).
-- =====================================================================

ALTER TABLE financial_movements ADD COLUMN IF NOT EXISTS estado_conciliacion VARCHAR(20);
UPDATE financial_movements SET estado_conciliacion = 'NO_CONCILIADO' WHERE estado_conciliacion IS NULL;

COMMENT ON COLUMN financial_movements.estado_conciliacion IS 'BNK-HU-069: NO_CONCILIADO | EN_REVISION | CONCILIADO. El estado PENDIENTE de libros se mapea a NO_CONCILIADO.';

-- Cabecera del emparejamiento. tipo: UNO_A_UNO|N_A_UNO|UNO_A_N|N_A_N.
-- metodo: AUTOMATICO_EXACTO|AUTOMATICO_ALTO|AUTOMATICO_MEDIO|AUTOMATICO_AGREGADO|MANUAL.
-- estado: CONFIRMADO|PROPUESTO|AMBIGUO|DESHECHO.
CREATE TABLE IF NOT EXISTS emparejamientos (
    id                        BIGSERIAL PRIMARY KEY,
    company_id                BIGINT NOT NULL,
    cuenta_bancaria_id        BIGINT,
    reconciliation_session_id BIGINT,
    tipo_emparejamiento       VARCHAR(16) NOT NULL,
    metodo                    VARCHAR(24) NOT NULL,
    score                     INTEGER NOT NULL DEFAULT 0,
    estado                    VARCHAR(16) NOT NULL,
    suma_extracto             NUMERIC(20,2) NOT NULL DEFAULT 0,
    suma_libros               NUMERIC(20,2) NOT NULL DEFAULT 0,
    diferencia                NUMERIC(20,2) NOT NULL DEFAULT 0,
    motivo_match_manual       VARCHAR(500),
    parametros_usados         VARCHAR(1000),
    confirmado_at             TIMESTAMP,
    confirmado_by             VARCHAR(120),
    created_at                TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at                TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_emp_cuenta  ON emparejamientos (cuenta_bancaria_id);
CREATE INDEX IF NOT EXISTS idx_emp_company ON emparejamientos (company_id);
CREATE INDEX IF NOT EXISTS idx_emp_estado  ON emparejamientos (estado);

-- Detalle del emparejamiento: una fila por cada movimiento de cada lado.
-- lado: EXTRACTO | LIBROS.
CREATE TABLE IF NOT EXISTS emparejamiento_detalle (
    id                    BIGSERIAL PRIMARY KEY,
    company_id            BIGINT NOT NULL,
    emparejamiento_id     BIGINT NOT NULL,
    financial_movement_id BIGINT NOT NULL,
    lado                  VARCHAR(8) NOT NULL,
    monto                 NUMERIC(20,2) NOT NULL,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_empdet_emp ON emparejamiento_detalle (emparejamiento_id);
CREATE INDEX IF NOT EXISTS idx_empdet_mov ON emparejamiento_detalle (financial_movement_id);
