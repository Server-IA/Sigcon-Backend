-- ============================================================
-- BNK-RF-29 / BNK-RF-30 / BNK-RF-31 / BNK-RF-32
-- Módulo: Flujo de Caja — Proyecciones
--
-- Crea la tabla principal de proyecciones de flujo de caja.
-- No depende de empresa (companyId).
-- Soporta eliminación lógica mediante deleted_at.
-- ============================================================

CREATE TABLE IF NOT EXISTS bnk_cash_flow_projections (
    id               BIGSERIAL PRIMARY KEY,

    -- Identificación
    name             VARCHAR(255) NOT NULL,
    description      VARCHAR(500),

    -- Período
    start_date       DATE         NOT NULL,
    end_date         DATE         NOT NULL,

    -- Clasificación
    periodicity      VARCHAR(30)  NOT NULL,
    projection_type  VARCHAR(20)  NOT NULL,

    -- Saldos (BNK-RF-29: finalBalance = initialBalance + netFlow, calculado en backend)
    initial_balance  NUMERIC(19, 2) NOT NULL,
    net_flow         NUMERIC(19, 2) NOT NULL,
    final_balance    NUMERIC(19, 2) NOT NULL,

    -- Moneda ISO 4217 (vincula con cuentas contables/bancarias)
    currency         CHAR(3)      NOT NULL,

    -- Ciclo de vida (BNK-RF-31)
    status           VARCHAR(20)  NOT NULL DEFAULT 'BORRADOR',

    -- BNK-RF-30: motivo requerido al modificar proyecciones APROBADAS
    modification_reason VARCHAR(500),

    -- Auditoría básica
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),

    -- Eliminación lógica (BNK-RF-31)
    -- Cuando deleted_at IS NOT NULL el registro se considera eliminado.
    -- La capa JPA (@Where clause) excluye estos registros automáticamente.
    deleted_at       TIMESTAMP    NULL
);

-- ─── Índices ────────────────────────────────────────────────

-- Unicidad del nombre (solo entre registros activos)
CREATE UNIQUE INDEX IF NOT EXISTS uidx_bnk_cfp_name_active
    ON bnk_cash_flow_projections (name)
    WHERE deleted_at IS NULL;

-- Consultas frecuentes por estado
CREATE INDEX IF NOT EXISTS idx_bnk_cfp_status
    ON bnk_cash_flow_projections (status)
    WHERE deleted_at IS NULL;

-- Consultas por período
CREATE INDEX IF NOT EXISTS idx_bnk_cfp_dates
    ON bnk_cash_flow_projections (start_date, end_date)
    WHERE deleted_at IS NULL;

-- ─── Comentarios de columnas ────────────────────────────────

COMMENT ON TABLE  bnk_cash_flow_projections IS
    'Proyecciones de flujo de caja (BNK-RF-29 a BNK-RF-32). Sin dependencia de empresa.';

COMMENT ON COLUMN bnk_cash_flow_projections.final_balance IS
    'Saldo final calculado por el sistema: initialBalance + netFlow. No aceptado del cliente.';

COMMENT ON COLUMN bnk_cash_flow_projections.status IS
    'Estados: BORRADOR | APROBADA | EJECUTADA | INACTIVA';

COMMENT ON COLUMN bnk_cash_flow_projections.deleted_at IS
    'Eliminación lógica (BNK-RF-31). NULL = activo. NOT NULL = eliminado lógicamente.';

COMMENT ON COLUMN bnk_cash_flow_projections.currency IS
    'Código de moneda ISO 4217 (ej. COP, USD, EUR). Vincula con cuentas contables/bancarias.';

COMMENT ON COLUMN bnk_cash_flow_projections.modification_reason IS
    'Requerido cuando se modifica una proyección en estado APROBADA (BNK-RF-30).';
