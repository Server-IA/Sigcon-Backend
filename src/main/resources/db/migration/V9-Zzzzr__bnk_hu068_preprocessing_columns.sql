-- =====================================================================
-- BNK-HU-068 — Columnas de pre-procesamiento en financial_movements.
-- Normalización + referencias extraídas + clasificación. ADITIVAS (R4),
-- todas nullable. Idempotente.
-- =====================================================================

ALTER TABLE financial_movements ADD COLUMN IF NOT EXISTS descripcion_normalizada VARCHAR(500);
ALTER TABLE financial_movements ADD COLUMN IF NOT EXISTS numero_cheque VARCHAR(40);
ALTER TABLE financial_movements ADD COLUMN IF NOT EXISTS nit_detectado VARCHAR(20);
ALTER TABLE financial_movements ADD COLUMN IF NOT EXISTS tipo_movimiento VARCHAR(40);
ALTER TABLE financial_movements ADD COLUMN IF NOT EXISTS clasificacion_confianza INTEGER;
ALTER TABLE financial_movements ADD COLUMN IF NOT EXISTS cuenta_puc_sugerida VARCHAR(20);

COMMENT ON COLUMN financial_movements.descripcion_normalizada IS 'BNK-HU-068 E2: descripción normalizada (upper, sin tildes, [A-Z0-9 ./-])';
COMMENT ON COLUMN financial_movements.tipo_movimiento IS 'BNK-HU-068 E6/E7: tipo asignado por reglas o fallback DEPOSITO/RETIRO';
COMMENT ON COLUMN financial_movements.clasificacion_confianza IS 'BNK-HU-068: 90 regla, 30 fallback, 100 corrección manual';
