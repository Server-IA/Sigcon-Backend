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
INSERT INTO parameters (category, name, value, description, status, created_at, updated_at)
SELECT 'INTEGRATION_AGROFUSION', 'AGROFUSION_ACK_RETRY_MAX_ATTEMPTS', '3',
       'Numero maximo de intentos de envio del ACK antes de marcar ACK_FAILED definitivo (HU-INT-RF-13 E3)',
       'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name='AGROFUSION_ACK_RETRY_MAX_ATTEMPTS' AND deleted_at IS NULL);

INSERT INTO parameters (category, name, value, description, status, created_at, updated_at)
SELECT 'INTEGRATION_AGROFUSION', 'AGROFUSION_ACK_RETRY_INITIAL_DELAY_SECONDS', '60',
       'Delay inicial del backoff exponencial en segundos. Intentos: 60s, 120s, 240s (HU-INT-RF-13 E1/E2)',
       'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name='AGROFUSION_ACK_RETRY_INITIAL_DELAY_SECONDS' AND deleted_at IS NULL);

-- Apuntar callback al mock por defecto. UPDATE para sobrescribir el valor
-- de produccion 'https://api.agrofusion.co/integrations/aaef/ack' que se
-- inserto en V32 (este endpoint no existe aun).
UPDATE parameters
SET value = 'http://localhost:8080/mock-agrofusion/aaef/ack',
    description = 'URL callback ACK. Por defecto apunta al mock embebido (Fase 7). En produccion: https://api.agrofusion.co/integrations/aaef/ack',
    updated_at = NOW()
WHERE name = 'AGROFUSION_ACK_CALLBACK_URL' AND deleted_at IS NULL;
