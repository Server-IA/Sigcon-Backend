-- PA-RF-28 (Pendientes PA, 2026-06-03): ciclo de vida completo de API Keys (AAEF).
--
-- Hasta ahora la integracion AAEF usaba la(s) clave(s) GLOBAL(es) en la tabla
-- parameters (AGROFUSION_API_KEY / AGROFUSION_API_KEY_TEST) validadas en texto
-- plano por ApiKeyFilter. El RF pide una gestion completa: emision con hash
-- SHA-256 (nunca texto plano), maximo 2 activas por empresa, expiracion a 365
-- dias, revocacion con motivo, listado de metadata y aviso 30 dias antes de
-- expirar.
--
-- ADITIVO Y NO DESTRUCTIVO: la clave global legacy sigue siendo valida (el
-- ApiKeyFilter la prueba primero). Las claves de esta tabla se aceptan ADEMAS,
-- por su hash. Asi el flujo AAEF actual con AgroFusion no se rompe.
--
-- Idempotente: CREATE TABLE/INDEX IF NOT EXISTS.

CREATE TABLE IF NOT EXISTS api_keys (
    id                BIGSERIAL PRIMARY KEY,
    company_id        BIGINT NOT NULL REFERENCES companies(id),
    key_hash          VARCHAR(255) NOT NULL,            -- SHA-256 de la key completa, NUNCA texto plano
    prefix            VARCHAR(40)  NOT NULL,            -- parte publica (SIGCON-AAEF-<publicId>) para identificarla
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | REVOKED | EXPIRED
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMP    NOT NULL,            -- default en el service: created_at + 365 dias
    last_used_at      TIMESTAMP,
    revoked_at        TIMESTAMP,
    revocation_reason VARCHAR(200),
    created_by        BIGINT REFERENCES users(id),
    -- Evita re-notificar el aviso de proxima expiracion en cada corrida del scheduler.
    notified_expiry   BOOLEAN NOT NULL DEFAULT FALSE
);

-- Lookup por hash en cada request AAEF (ApiKeyFilter).
CREATE INDEX IF NOT EXISTS idx_api_keys_hash ON api_keys (key_hash);
-- Conteo de activas por empresa (regla de maximo 2) + listado.
CREATE INDEX IF NOT EXISTS idx_api_keys_company ON api_keys (company_id, status);
-- Scheduler: marcar expiradas + avisar proximas a expirar.
CREATE INDEX IF NOT EXISTS idx_api_keys_expiry ON api_keys (status, expires_at);
