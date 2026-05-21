-- =====================================================================
-- BNK-HU-062 / BNK-HU-063 — Conservación de soportes (extracto bancario,
-- informes) con hash SHA-256 y retención de 10 años.
-- Tabla nueva (regla R4: CREATE TABLE para tabla inexistente). Multi-tenant.
-- Idempotente (CREATE TABLE IF NOT EXISTS).
-- =====================================================================

CREATE TABLE IF NOT EXISTS archivos_soporte (
    id                        BIGSERIAL PRIMARY KEY,
    company_id                BIGINT NOT NULL,
    tipo                      VARCHAR(40) NOT NULL,        -- EXTRACTO_BANCARIO | CSV_MOVIMIENTOS | INFORME_CONCILIACION | OTRO
    file_name                 VARCHAR(255) NOT NULL,
    mime_type                 VARCHAR(120),
    file_content              BYTEA NOT NULL,              -- bytes originales (sin @Lob: Hibernate 6 mapea byte[] a OID)
    hash_sha256               VARCHAR(64) NOT NULL,        -- BNK-HU-062 E1: hash inalterable del archivo
    file_size                 BIGINT NOT NULL DEFAULT 0,
    bank_account_id           BIGINT,
    reconciliation_session_id BIGINT,
    uploaded_by               BIGINT,
    uploaded_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    retener_hasta             TIMESTAMP,                   -- BNK-HU-063 E1: fecha_carga + 10 años
    replication_status        VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | OK | FAILED (E2/E3: replicación = infra)
    replicated_at             TIMESTAMP,
    deleted_at                TIMESTAMP                    -- soft-archive solo permitido tras retener_hasta + acta (E5)
);

CREATE INDEX IF NOT EXISTS idx_archivos_soporte_company   ON archivos_soporte (company_id);
CREATE INDEX IF NOT EXISTS idx_archivos_soporte_hash      ON archivos_soporte (company_id, hash_sha256);
CREATE INDEX IF NOT EXISTS idx_archivos_soporte_account   ON archivos_soporte (bank_account_id);

COMMENT ON TABLE archivos_soporte IS 'BNK-HU-062/063: soportes conservados con hash SHA-256 y retención 10 años';
COMMENT ON COLUMN archivos_soporte.hash_sha256 IS 'BNK-HU-062 E1/E4: hash inalterable; verificación recalcula sobre file_content';
COMMENT ON COLUMN archivos_soporte.retener_hasta IS 'BNK-HU-063 E1: fecha_carga + 10 años; bloquea borrado físico antes (E5)';
COMMENT ON COLUMN archivos_soporte.replication_status IS 'BNK-HU-063 E2/E3: replicación a medio alterno = infraestructura (no disponible local)';
