-- ============================================================
-- ACT-RF-02: Cálculo Automático de Depreciación
-- Agrega campos necesarios para persistir el resultado
-- del cálculo de depreciación por período contable.
-- ============================================================

ALTER TABLE assets
    ADD COLUMN IF NOT EXISTS current_book_value  NUMERIC(19, 2) NULL,
    ADD COLUMN IF NOT EXISTS last_depreciation_date DATE NULL;

COMMENT ON COLUMN assets.current_book_value IS
    'Valor neto en libros del activo después de depreciación acumulada (ACT-RF-02)';

COMMENT ON COLUMN assets.last_depreciation_date IS
    'Fecha del último cálculo de depreciación aplicado al activo (ACT-RF-02)';
