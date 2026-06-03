-- PT-03 / PT-10 (TER-RF-11/12, 2026-06-02): motivo del cambio (o justificacion
-- de eliminacion) en el historial de datos comerciales. Idempotente.
-- Hibernate ddl-auto=update tambien agrega la columna desde la entidad; esta
-- migracion la hace explicita para entornos sin ddl-auto.
ALTER TABLE commercial_data_history ADD COLUMN IF NOT EXISTS change_reason TEXT;
