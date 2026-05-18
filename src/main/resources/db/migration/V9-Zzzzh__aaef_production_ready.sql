-- QA Bloque BM (2026-05-18): preparar parametros AAEF para produccion.
--
-- Contexto:
--   El proyecto venia con valores placeholder en parametros AAEF:
--     - AGROFUSION_API_KEY = 'changeme-in-production-...'
--     - AGROFUSION_JWT_ENABLED = 'true' apuntando a JWKS no real
--     - URLs de IdP y callback con placeholder o localhost (en local)
--
--   Para ir a produccion el admin debe ejecutar el endpoint
--   POST /api/contabilidad/admin/aaef-status/rotate-api-key?type=PROD
--   para generar una API Key robusta. PERO si nadie lo hace, el sistema
--   se quedaria con 'changeme-...' aceptado en prod, lo cual es un riesgo.
--
-- Esta migracion:
--   1. Detecta API Keys que empiezan con 'changeme-' y las rota a una clave
--      generada (Postgres pseudo-aleatoria). El admin DEBE rotar de nuevo
--      via endpoint para tener evidencia auditable de la rotacion.
--   2. Pone AGROFUSION_JWT_ENABLED en 'false' por defecto si no esta
--      configurado el IdP real (AGROFUSION_JWT_ISSUER apunta a localhost o
--      placeholder). El flujo X-API-Key sigue funcionando OK.
--   3. Agrega parametro AGROFUSION_RETENTION_YEARS (5 anios default) si no
--      existe.
--
-- Idempotente: si los valores ya estan en estado prod, no hace cambios.

-- ============================================================
-- 1. Rotar AGROFUSION_API_KEY si tiene placeholder
-- ============================================================
DO $$
DECLARE
    new_key TEXT;
BEGIN
    -- Generar key alfanumerica usando md5 con timestamp para entropia.
    -- Usamos 3 md5 concatenados para garantizar >= 64 chars sin caracteres especiales.
    -- md5() es portable (no requiere pgcrypto extension).
    new_key := 'SIGCON-AAEF-PROD-'
            || md5(random()::text || clock_timestamp()::text)
            || md5(random()::text || clock_timestamp()::text);
    new_key := substring(new_key, 1, 80);  -- max length parameters.value=255 OK

    UPDATE parameters
       SET value = new_key,
           updated_at = NOW()
     WHERE name = 'AGROFUSION_API_KEY'
       AND deleted_at IS NULL
       AND (value LIKE 'changeme-%' OR value LIKE '%placeholder%' OR LENGTH(value) < 32);

    IF FOUND THEN
        RAISE NOTICE 'V9-Zzzzh: AGROFUSION_API_KEY rotada (placeholder detectado). El admin debe rotar de nuevo via endpoint para auditoria.';
    END IF;
END $$;

-- ============================================================
-- 2. Deshabilitar JWT por defecto si IdP no esta configurado realmente
-- ============================================================
-- Mientras AgroFusion no exponga su IdP real, mantener JWT_ENABLED=false
-- evita errores de JWKS_UNAVAILABLE en cada request con Bearer header.
-- El flujo X-API-Key (que es lo que AgroFusion usa hoy) sigue 100% funcional.
-- Cuando AgroFusion exponga el IdP real:
--   1. UPDATE AGROFUSION_JWT_ISSUER = url real
--   2. UPDATE AGROFUSION_JWKS_URL = url real
--   3. UPDATE AGROFUSION_JWT_ENABLED = 'true'
--   4. POST /api/contabilidad/admin/jwt-config/reload
DO $$
DECLARE
    current_issuer TEXT;
BEGIN
    SELECT value INTO current_issuer FROM parameters
     WHERE name = 'AGROFUSION_JWT_ISSUER' AND deleted_at IS NULL LIMIT 1;

    -- Si el issuer NO es real (apunta a localhost, mock, o placeholder de agrofusion.co
    -- que aun no responde), forzar JWT_ENABLED=false. Asi el filtro JWT NO intenta
    -- bajar JWKS innecesariamente.
    IF current_issuer IS NULL OR current_issuer LIKE '%localhost%'
       OR current_issuer LIKE '%mock%' OR current_issuer LIKE '%sso.agrofusion.co%' THEN
        UPDATE parameters
           SET value = 'false',
               updated_at = NOW()
         WHERE name = 'AGROFUSION_JWT_ENABLED'
           AND deleted_at IS NULL
           AND value = 'true';
        IF FOUND THEN
            RAISE NOTICE 'V9-Zzzzh: AGROFUSION_JWT_ENABLED forzado a false (IdP no esta configurado realmente). Cuando AgroFusion exponga IdP real, actualizar issuer/jwks y reactivar.';
        END IF;
    END IF;
END $$;

-- ============================================================
-- 3. Asegurar parametro AGROFUSION_RETENTION_YEARS (default 5)
-- ============================================================
-- Spec AAEF: retencion de logs 5 anios (Estatuto Tributario Art. 632).
-- El scheduler IntegrationRetentionScheduler lo lee para purgar lotes viejos.
INSERT INTO parameters (name, value, category, status, company_id, created_at, updated_at)
SELECT 'AGROFUSION_RETENTION_YEARS', '5', 'INTEGRATION_AGROFUSION', 'ACTIVE', 1, NOW(), NOW()
 WHERE NOT EXISTS (
       SELECT 1 FROM parameters
        WHERE name = 'AGROFUSION_RETENTION_YEARS' AND deleted_at IS NULL);

-- ============================================================
-- 4. Asegurar AGROFUSION_MAX_BATCH_SIZE_MB (default 20 MB segun spec)
-- ============================================================
INSERT INTO parameters (name, value, category, status, company_id, created_at, updated_at)
SELECT 'AGROFUSION_MAX_BATCH_SIZE_MB', '20', 'INTEGRATION_AGROFUSION', 'ACTIVE', 1, NOW(), NOW()
 WHERE NOT EXISTS (
       SELECT 1 FROM parameters
        WHERE name = 'AGROFUSION_MAX_BATCH_SIZE_MB' AND deleted_at IS NULL);

-- ============================================================
-- 5. Resumen (informativo)
-- ============================================================
-- Despues de esta migracion:
--   - API Key robusta (admin DEBE rotar de nuevo via endpoint para auditoria)
--   - JWT deshabilitado si IdP no es real (X-API-Key sigue funcionando)
--   - Retencion 5 anios configurada
--   - Max batch size 20 MB configurado
-- El callback URL se gestiona en V9-ZZZZF (apunta a inmero.co/agrofusion/test).
