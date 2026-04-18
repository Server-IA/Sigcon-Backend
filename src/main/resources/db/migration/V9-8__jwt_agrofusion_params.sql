-- V9-8: Parametros de autenticacion JWT para la integracion AAEF (HU-INT-RF-11).
--
-- Agrega los parametros necesarios para validar tokens JWT emitidos por el SSO
-- de AgroFusion (issuer, JWKS URL, scope requerido).
--
-- En ambientes de desarrollo/prueba, AGROFUSION_JWKS_URL puede apuntar al
-- endpoint mock expuesto por el propio SIGCON (/mock-idp/.well-known/jwks.json)
-- para facilitar smoke tests sin depender de un IdP externo.

INSERT INTO parameters (category, name, value, description, status, created_at, updated_at)
SELECT 'INTEGRATION_AGROFUSION', 'AGROFUSION_JWT_ISSUER',
       'http://localhost:8080/mock-idp',
       'Issuer esperado en el claim "iss" del JWT. En produccion: https://sso.agrofusion.co',
       'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name = 'AGROFUSION_JWT_ISSUER' AND deleted_at IS NULL);

INSERT INTO parameters (category, name, value, description, status, created_at, updated_at)
SELECT 'INTEGRATION_AGROFUSION', 'AGROFUSION_JWKS_URL',
       'http://localhost:8080/mock-idp/.well-known/jwks.json',
       'URL del JWKS para obtener las claves publicas del IdP. En produccion: https://sso.agrofusion.co/.well-known/jwks.json',
       'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name = 'AGROFUSION_JWKS_URL' AND deleted_at IS NULL);

INSERT INTO parameters (category, name, value, description, status, created_at, updated_at)
SELECT 'INTEGRATION_AGROFUSION', 'AGROFUSION_JWT_SCOPE_REQUIRED',
       'aaef:lote:enviar',
       'Scope requerido en el claim "scope" del JWT para aceptar requests a /api/contabilidad/**',
       'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name = 'AGROFUSION_JWT_SCOPE_REQUIRED' AND deleted_at IS NULL);

INSERT INTO parameters (category, name, value, description, status, created_at, updated_at)
SELECT 'INTEGRATION_AGROFUSION', 'AGROFUSION_JWT_ENABLED',
       'true',
       'Si true, valida JWT RS256 en requests a /api/contabilidad/** con prioridad sobre X-API-Key',
       'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name = 'AGROFUSION_JWT_ENABLED' AND deleted_at IS NULL);
