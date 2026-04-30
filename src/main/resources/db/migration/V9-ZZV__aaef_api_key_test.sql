-- V9-ZZV (2026-04-30): segunda API Key AAEF para entorno de pruebas.
--
-- Contexto:
--   - AGROFUSION_API_KEY (V32) es la key PRODUCTIVA que usa AgroFusion. Tiene
--     rate limit estricto: 10 lotes/hora (RF-INT-12 R12 + HU-INT-RF-13).
--   - QA / integradores externos necesitan una key alterna con rate limit
--     mayor (>= 40 lotes/hora) para correr suites end-to-end sin esperar
--     ventanas de 1 hora entre baterias.
--
-- Esta migracion siembra el parametro AGROFUSION_API_KEY_TEST. La logica de
-- ApiKeyFilter acepta cualquiera de las dos keys, y AaefRateLimitFilter
-- aplica el limite que corresponde al tier de la key recibida:
--   - PRODUCTION: 10 lotes/hora
--   - TEST:       50 lotes/hora
--
-- Idempotente: si el parametro ya existe en company_id=1 (fuente autoritativa
-- de config global, igual que AGROFUSION_API_KEY), no toca nada.
--
-- Rotacion: para regenerar la key TEST, hacer UPDATE manual via psql o desde
-- el modulo Plataforma -> Parametros (PLATFORM_ADMIN). El cambio aplica
-- inmediato porque ApiKeyFilter consulta la BD en cada request via
-- ParameterRepository.findGlobalValueByName.

INSERT INTO parameters (company_id, name, value, description, category, status, created_at, updated_at)
SELECT 1, 'AGROFUSION_API_KEY_TEST',
       'SIGCON-AAEF-TEST-' || replace(md5(random()::text || clock_timestamp()::text), '-', '')
                            || replace(md5(random()::text || clock_timestamp()::text), '-', ''),
       'API Key de PRUEBAS para integraciones AAEF (rate limit 50 lotes/hora). NO usar en produccion. Header X-API-Key.',
       'INTEGRATION_AGROFUSION', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM parameters
     WHERE name = 'AGROFUSION_API_KEY_TEST'
       AND company_id = 1
       AND deleted_at IS NULL
);
