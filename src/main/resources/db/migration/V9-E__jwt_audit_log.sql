-- V9-E: tabla jwt_audit_log para forensia de tokens JWT validados.
--
-- HU-INT-RF-11 (deuda tecnica complementaria): cada token Bearer recibido en
-- /api/contabilidad/** se audita aqui. Permite responder preguntas como:
--   * Que tokens se aceptaron entre las 14:00 y las 15:00 ayer?
--   * Cuantos requests rechazo el filtro JWT por scope insuficiente este mes?
--   * Que kid se uso (para detectar uso de claves rotadas)?
--   * Que IP envio tokens validos vs invalidos?
--
-- Append-only: NO tiene deleted_at, el endpoint solo expone GET. La purga la
-- hace el modulo de retencion (V9-B) si se quiere, pero por defecto crece
-- indefinidamente para forensia.

CREATE TABLE IF NOT EXISTS jwt_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    -- Claims del token (kid, sub, iat, iss, exp)
    kid             VARCHAR(150),
    subject         VARCHAR(255),
    issued_at       TIMESTAMP,
    expires_at      TIMESTAMP,
    issuer          VARCHAR(500),
    scope           VARCHAR(255),
    -- Resultado: VALID / EXPIRED / INVALID_SCOPE / WRONG_ISSUER / INVALID_SIGNATURE / MALFORMED / JWKS_UNAVAILABLE
    result          VARCHAR(30) NOT NULL,
    -- Detalle del error si fallo
    error_message   VARCHAR(500),
    -- Contexto del request
    remote_ip       VARCHAR(60),
    user_agent      VARCHAR(500),
    request_path    VARCHAR(255),
    -- Cuando ocurrio
    validated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indices para queries comunes
CREATE INDEX IF NOT EXISTS idx_jwt_audit_validated
    ON jwt_audit_log (validated_at DESC);
CREATE INDEX IF NOT EXISTS idx_jwt_audit_kid
    ON jwt_audit_log (kid);
CREATE INDEX IF NOT EXISTS idx_jwt_audit_subject
    ON jwt_audit_log (subject);
CREATE INDEX IF NOT EXISTS idx_jwt_audit_result
    ON jwt_audit_log (result, validated_at DESC);

COMMENT ON TABLE jwt_audit_log IS
    'V9-E / HU-INT-RF-11 (forensia): registro append-only de tokens JWT validados '
    'por AgroFusionJwtFilter. NO tiene deleted_at - solo INSERT y SELECT.';
COMMENT ON COLUMN jwt_audit_log.kid IS 'Key ID del JWK que firmo el token (header)';
COMMENT ON COLUMN jwt_audit_log.subject IS 'Claim sub del JWT (usuario emisor)';
COMMENT ON COLUMN jwt_audit_log.issued_at IS 'Claim iat del JWT';
