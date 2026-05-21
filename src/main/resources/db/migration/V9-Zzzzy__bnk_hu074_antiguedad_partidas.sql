-- =====================================================================
-- BNK-HU-074: antigüedad de partidas conciliatorias + alertas + caducidad cheques.
-- =====================================================================
-- Amplía partidas_conciliatorias con campos de antigüedad/alertas (Hibernate
-- ddl-auto también los crea desde la entidad; ADD COLUMN IF NOT EXISTS es
-- idempotente y documenta el esquema). Aditivo (R2/R3). Prefijo 'y' => ordena
-- después de V9-Zzzzx.
-- =====================================================================

-- HU-074 E8: el estado RESUELTA_PROXIMO_PERIODO (24 chars) excede el VARCHAR(20)
-- original; ampliar a 30 (Hibernate ddl-auto no acorta/alarga columnas existentes).
ALTER TABLE partidas_conciliatorias ALTER COLUMN estado TYPE VARCHAR(30);

ALTER TABLE partidas_conciliatorias ADD COLUMN IF NOT EXISTS dias_antiguedad   INTEGER;
ALTER TABLE partidas_conciliatorias ADD COLUMN IF NOT EXISTS fecha_origen      DATE;
ALTER TABLE partidas_conciliatorias ADD COLUMN IF NOT EXISTS alerta_60d_at     TIMESTAMP;
ALTER TABLE partidas_conciliatorias ADD COLUMN IF NOT EXISTS alerta_90d_at     TIMESTAMP;
ALTER TABLE partidas_conciliatorias ADD COLUMN IF NOT EXISTS motivo_resolucion VARCHAR(500);

-- Backfill fecha_origen desde la fecha del movimiento del extracto (si no está).
UPDATE partidas_conciliatorias p
   SET fecha_origen = fm.movement_date
  FROM financial_movements fm
 WHERE p.financial_movement_id = fm.id
   AND p.fecha_origen IS NULL;

CREATE INDEX IF NOT EXISTS idx_partidas_conc_dias ON partidas_conciliatorias (dias_antiguedad);

-- ----- Menú de UI (Bancos y Cajas)
DO $$
DECLARE v_bnk BIGINT;
BEGIN
    SELECT id INTO v_bnk FROM modules WHERE name = 'Bancos y Cajas' AND deleted_at IS NULL LIMIT 1;
    IF v_bnk IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, required_permission_code, created_at, updated_at)
        SELECT 'Antigüedad de Partidas', 'ri-time-line', 'partidas-antiguedad', 22, v_bnk, 'ACTIVE', 'PARTIDAS_ANTIGUEDAD', true, 'BNK.CUENTAS.VER', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'PARTIDAS_ANTIGUEDAD' AND deleted_at IS NULL);
    END IF;
END $$;
