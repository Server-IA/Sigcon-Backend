-- =====================================================================
-- Conciliación bancaria (flujo guiado) — F2: vincular el extracto importado
-- a la sesión rica de conciliación (mapeo I1 por R4, sin tabla separada).
-- Aditivo e idempotente. Hibernate ddl-auto crea la columna desde la entidad;
-- este ALTER es una red de seguridad + el índice de apoyo para las consultas
-- de "extracto del período".
-- Nombre V9-Zzzzzb: ordena lexicalmente DESPUES de V9-Zzzzza (FASE 7 DIAN) y
-- de V9-Z__multi (las funciones de tenant), sin dependencias entre ellas.
-- =====================================================================

ALTER TABLE financial_movements
    ADD COLUMN IF NOT EXISTS sesion_conciliacion_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_fm_sesion_concil
    ON financial_movements (sesion_conciliacion_id);
