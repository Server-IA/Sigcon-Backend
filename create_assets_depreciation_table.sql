-- =================================================================
-- ACT-RF-02: Tabla de histórico de depreciaciones de activos
-- =================================================================
-- Esta tabla almacena un registro inmutable de cada depreciación
-- aplicada a un activo. Es generada automáticamente por el sistema
-- en cada ejecución del cálculo de depreciación.
-- =================================================================

CREATE TABLE assets_depreciation (
    id                   BIGSERIAL     PRIMARY KEY,
    asset_id             BIGINT        NOT NULL,
    depreciation_period  VARCHAR(7)    NOT NULL,
    previous_book_value  NUMERIC(19,2) NOT NULL,
    current_book_value   NUMERIC(19,2) NOT NULL,
    depreciation_amount  NUMERIC(19,2) NOT NULL,
    depreciation_method  VARCHAR(40)   NOT NULL,
    calculation_date     DATE          NOT NULL,
    created_at           TIMESTAMP     NOT NULL,

    CONSTRAINT fk_assets_depreciation_asset
        FOREIGN KEY (asset_id) REFERENCES assets(id)
);

-- Índices para mejorar las consultas más frecuentes
CREATE INDEX idx_assets_depreciation_asset_id
    ON assets_depreciation (asset_id);

CREATE INDEX idx_assets_depreciation_period
    ON assets_depreciation (depreciation_period);
