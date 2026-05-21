-- =====================================================================
-- BNK-HU-071 — Catálogo de reglas de clasificación para el pre-procesamiento.
-- Tabla nueva (R4) multi-tenant. Seed de 9 reglas globales por empresa activa.
-- Idempotente.
-- =====================================================================

CREATE TABLE IF NOT EXISTS reglas_clasificacion (
    id                   BIGSERIAL PRIMARY KEY,
    company_id           BIGINT NOT NULL,
    nombre               VARCHAR(120) NOT NULL,
    prioridad            INTEGER NOT NULL DEFAULT 100,    -- 1..999 (ASC = se evalúa primero)
    patron_regex         VARCHAR(500) NOT NULL,
    signo                VARCHAR(12) NOT NULL DEFAULT 'CUALQUIERA', -- DEBITO | CREDITO | CUALQUIERA
    monto_min            NUMERIC(20,2),
    monto_max            NUMERIC(20,2),
    tipo_movimiento      VARCHAR(40) NOT NULL,            -- GMF | COMISION | INTERES_GANADO | ...
    cuenta_puc_sugerida  VARCHAR(20),                     -- código PUC sugerido (ej. 530525)
    alcance              VARCHAR(12) NOT NULL DEFAULT 'GLOBAL', -- GLOBAL | BANCO | CUENTA
    banco_id             BIGINT,
    cuenta_bancaria_id   BIGINT,
    activa               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at           TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reglas_clasif_company   ON reglas_clasificacion (company_id);
CREATE INDEX IF NOT EXISTS idx_reglas_clasif_prioridad ON reglas_clasificacion (company_id, prioridad);

-- Seed de 9 reglas globales por cada empresa activa (BNK-HU-071 E1).
DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN SELECT id FROM companies WHERE deleted_at IS NULL LOOP
        -- Nota: Hibernate ddl-auto crea la tabla ANTES de esta migracion, SIN los
        -- DEFAULT del CREATE TABLE. Por eso el seed debe proveer explicitamente todas
        -- las columnas NOT NULL (activa, created_at, updated_at).
        INSERT INTO reglas_clasificacion (company_id, nombre, prioridad, patron_regex, signo, tipo_movimiento, cuenta_puc_sugerida, alcance, activa, created_at, updated_at)
        SELECT c.id, v.nombre, v.prioridad, v.patron, v.signo, v.tipo, v.cuenta, 'GLOBAL', TRUE, NOW(), NOW()
        FROM (VALUES
            ('GMF 4x1000',        10, 'GMF|4X1000|GRAVAMEN MOVIMIENT',          'DEBITO',     'GMF',            '530525'),
            ('Comisión bancaria', 20, 'COMISION|CUOTA MANEJO|CUOTA DE MANEJO',  'DEBITO',     'COMISION',       '530505'),
            ('Interés ganado',    30, 'INTERES GANAD|ABONO INTERES|RENDIMIENTO','CREDITO',    'INTERES_GANADO', '421005'),
            ('Interés pagado',    40, 'INTERES PAGAD|INTERES MORA|INT MORA',    'DEBITO',     'INTERES_PAGADO', '530520'),
            ('Cheque',            50, 'CHEQUE|CHQ|CK ',                         'CUALQUIERA', 'CHEQUE',         NULL),
            ('PSE',               60, 'PSE|PAGO ELECTRONICO|PAGO PSE',          'CUALQUIERA', 'PSE',            NULL),
            ('Transferencia',     70, 'TRANSFER|TRASLADO|GIRO',                 'CUALQUIERA', 'TRANSFERENCIA',  NULL),
            ('Impuesto',          80, 'IMPUESTO|RETENCION|DIAN',                'DEBITO',     'IMPUESTO',       NULL),
            ('Consignación',      90, 'CONSIGNACION|DEPOSITO|ABONO',            'CREDITO',    'CONSIGNACION',   NULL)
        ) AS v(nombre, prioridad, patron, signo, tipo, cuenta)
        WHERE NOT EXISTS (
            SELECT 1 FROM reglas_clasificacion r
            WHERE r.company_id = c.id AND r.nombre = v.nombre AND r.deleted_at IS NULL
        );
    END LOOP;
END $$;

COMMENT ON TABLE reglas_clasificacion IS 'BNK-HU-071: reglas de clasificación de movimientos del extracto (pre-procesamiento)';
