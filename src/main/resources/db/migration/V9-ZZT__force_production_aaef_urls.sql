-- V9-ZZT (2026-04-29): defensa contra URLs AAEF apuntando a localhost en
-- entornos productivos.
--
-- Contexto historico:
--   - V9-9 original tenia un UPDATE incondicional que sobrescribia
--     AGROFUSION_ACK_CALLBACK_URL a localhost en cada arranque. Bloque AL
--     elimino ese UPDATE pero las BDs que ya arrancaron con la version
--     vieja quedaron con el localhost grabado, y los INSERT con
--     WHERE NOT EXISTS no lo corrigen al re-deployar la version nueva.
--   - V9-8 siembra AGROFUSION_JWT_ISSUER y AGROFUSION_JWKS_URL con
--     localhost por defecto. Eso es OK para QA local, pero en produccion
--     deberia apuntar al SSO real de AgroFusion (cuando exista).
--
-- Esta migracion es un "fix forward" idempotente:
--   - Si la fila contiene "localhost" -> UPDATE al valor productivo.
--   - Si ya esta en valor productivo -> no toca nada (idempotente).
--
-- En desarrollo local con SIGCON_INTEGRATION_MOCKS_ENABLED=true, la clase
-- LocalAaefMockOverrides corre DESPUES de las migraciones (en
-- ApplicationReadyEvent) y vuelve a apuntar a localhost. Asi:
--   - Dokploy prod (sin la env var): esta migracion deja URLs prod.
--     LocalAaefMockOverrides no se carga. Resultado correcto.
--   - Local con mocks: esta migracion deja URLs prod. LocalAaefMockOverrides
--     se ejecuta y re-apunta a localhost. Resultado correcto para QA local.
--
-- Si en el futuro AgroFusion expone un IdP real distinto al placeholder,
-- actualizar las URLs aqui (o usar parametros de configuracion para
-- inyectar dinamicamente).

-- QA Bloque PA Bug 70 (2026-05-09): AgroFusion confirmo el callback real:
-- https://api.inmero.co/agrofusion/test/int/accounting-ACK
-- (antes era el placeholder agrofusion.co que no existe). V9-ZZZZF refuerza
-- esta URL en cada arranque; aqui solo nos aseguramos de no quedarnos en
-- localhost si la BD venia con mocks.
UPDATE parameters
   SET value = 'https://api.inmero.co/agrofusion/test/int/accounting-ACK',
       updated_at = NOW()
 WHERE name = 'AGROFUSION_ACK_CALLBACK_URL'
   AND deleted_at IS NULL
   AND value LIKE '%localhost%';

UPDATE parameters
   SET value = 'https://sso.agrofusion.co',
       updated_at = NOW()
 WHERE name = 'AGROFUSION_JWT_ISSUER'
   AND deleted_at IS NULL
   AND value LIKE '%localhost%';

UPDATE parameters
   SET value = 'https://sso.agrofusion.co/.well-known/jwks.json',
       updated_at = NOW()
 WHERE name = 'AGROFUSION_JWKS_URL'
   AND deleted_at IS NULL
   AND value LIKE '%localhost%';
