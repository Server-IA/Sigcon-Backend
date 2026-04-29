-- V9-9: Soporte para retry de ACK con backoff exponencial (HU-INT-RF-13).
--
-- Agrega:
--   - Columna ack_next_retry_at en integration_batches (instante del proximo intento)
--   - Parametros de configuracion del retry scheduler
--   - Apunta el callback de ACK al mock embebido en SIGCON para smoke tests
--     mientras AgroFusion no implemente su endpoint real

ALTER TABLE integration_batches
    ADD COLUMN IF NOT EXISTS ack_next_retry_at TIMESTAMP NULL;

-- ==========================================================================
-- Parametros de retry y mock callback
-- ==========================================================================
-- QA-BLOQUE-AN (2026-04-29): agregado company_id=1 explicito. Ver V32 para detalles.
INSERT INTO parameters (company_id, category, name, value, description, status, created_at, updated_at)
SELECT 1, 'INTEGRATION_AGROFUSION', 'AGROFUSION_ACK_RETRY_MAX_ATTEMPTS', '3',
       'Numero maximo de intentos de envio del ACK antes de marcar ACK_FAILED definitivo (HU-INT-RF-13 E3)',
       'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name='AGROFUSION_ACK_RETRY_MAX_ATTEMPTS' AND company_id = 1 AND deleted_at IS NULL);

INSERT INTO parameters (company_id, category, name, value, description, status, created_at, updated_at)
SELECT 1, 'INTEGRATION_AGROFUSION', 'AGROFUSION_ACK_RETRY_INITIAL_DELAY_SECONDS', '60',
       'Delay inicial del backoff exponencial en segundos. Intentos: 60s, 120s, 240s (HU-INT-RF-13 E1/E2)',
       'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name='AGROFUSION_ACK_RETRY_INITIAL_DELAY_SECONDS' AND company_id = 1 AND deleted_at IS NULL);

-- QA-BLOQUE-AL (2026-04-29): el override del callback URL al mock local se
-- movio a `LocalAaefMockOverrides` (Spring component @Profile("dev") +
-- @ConditionalOnProperty(sigcon.integration.mocks-enabled=true)).
--
-- Razon: la migracion no debe alterar el valor sembrado por V32
-- (https://api.agrofusion.co/integrations/aaef/ack) en produccion, porque
-- Dokploy no carga los mock controllers (estan tras
-- @ConditionalOnProperty) y el ACK terminaria en 404 -> ACK_FAILED en cada
-- lote.
--
-- En desarrollo local, `LocalAaefMockOverrides` corre tras `ApplicationReadyEvent`
-- y reapunta los valores hacia los mocks embebidos.
--
-- Si necesitas restaurar el valor productivo en una BD que ya tiene el
-- localhost grabado:
--   UPDATE parameters
--      SET value = 'https://api.agrofusion.co/integrations/aaef/ack', updated_at = NOW()
--    WHERE name = 'AGROFUSION_ACK_CALLBACK_URL' AND deleted_at IS NULL;
