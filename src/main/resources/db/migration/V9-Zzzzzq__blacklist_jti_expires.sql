-- PA-RF-27 (Pendientes PA, 2026-06-03): invalidacion de token con jti + expiracion.
--
-- La tabla blacklisted_tokens almacenaba unicamente el token completo (TEXT, ver
-- V9-Zzzzzo). El RF pide ademas el claim jti y la expiracion del JWT para poder
-- purgar las entradas vencidas (BlacklistCleanupScheduler) y no crecer sin limite.
--
-- Aditivo y no destructivo: el BlackListFilter sigue validando por la columna
-- `token`. Las columnas nuevas son nullable; las filas previas (sin jti/expires_at)
-- se conservan intactas y el job de limpieza las ignora (solo borra expires_at < now).
-- Idempotente: IF NOT EXISTS en columnas e indices.

ALTER TABLE blacklisted_tokens ADD COLUMN IF NOT EXISTS jti VARCHAR(255);
ALTER TABLE blacklisted_tokens ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

-- Indice por jti (consultas/trazabilidad por identificador de token).
CREATE INDEX IF NOT EXISTS idx_bt_jti ON blacklisted_tokens (jti);

-- Indice por expires_at: lo usa el job de limpieza (delete ... where expires_at < now).
CREATE INDEX IF NOT EXISTS idx_bt_expires_at ON blacklisted_tokens (expires_at);
