-- =====================================================================
-- BNK-HU-035 (ampliacion) E6/E7 + BNK-HU-023 E7/E13
-- Hash SHA-256 del archivo importado (import_file_hash) y hash por linea
-- (line_hash) para anti-duplicacion en la importacion de movimientos.
-- Columnas ADITIVAS (regla R4). Idempotente.
-- =====================================================================

ALTER TABLE financial_movements ADD COLUMN IF NOT EXISTS line_hash VARCHAR(64);
ALTER TABLE financial_movements ADD COLUMN IF NOT EXISTS import_file_hash VARCHAR(64);

-- BNK-HU-023 E13: constraint UNIQUE (cuenta_bancaria_id, hash_linea). Parcial:
-- solo aplica a movimientos importados (line_hash IS NOT NULL); los movimientos
-- manuales (line_hash NULL) no se ven afectados.
CREATE UNIQUE INDEX IF NOT EXISTS uk_fm_account_linehash
    ON financial_movements (bank_account_id, line_hash)
    WHERE line_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_fm_import_file_hash
    ON financial_movements (bank_account_id, import_file_hash)
    WHERE import_file_hash IS NOT NULL;

COMMENT ON COLUMN financial_movements.line_hash IS 'BNK-HU-035 E7: SHA-256(fecha+descripcion+monto+referencia+linea) anti-duplicado por fila';
COMMENT ON COLUMN financial_movements.import_file_hash IS 'BNK-HU-035 E6: SHA-256 del archivo CSV origen';
