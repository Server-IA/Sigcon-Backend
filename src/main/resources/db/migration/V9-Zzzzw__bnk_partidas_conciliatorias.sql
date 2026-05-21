-- =====================================================================
-- BNK-HU-061 / HU-073: partidas conciliatorias + menús de UI
-- =====================================================================
-- Una "partida conciliatoria" es un movimiento del extracto que el banco
-- cargó/abonó pero NO está en libros (GMF, comisión, intereses, notas) y
-- requiere un asiento de ajuste. HU-061 E1 las marca como candidatas durante
-- el pre-procesamiento; HU-073 E8 las resuelve cuando se genera el ajuste.
--
-- Hibernate ddl-auto crea la tabla desde la entidad ANTES de esta migración
-- (sin DEFAULTs); el CREATE TABLE IF NOT EXISTS documenta el esquema y los
-- índices/menús son el trabajo real. Idempotente, ADITIVO (R2/R3).
-- =====================================================================

CREATE TABLE IF NOT EXISTS partidas_conciliatorias (
    id                       BIGSERIAL PRIMARY KEY,
    company_id               BIGINT       NOT NULL,
    bank_account_id          BIGINT       NOT NULL,
    financial_movement_id    BIGINT       NOT NULL,
    tipo                     VARCHAR(40)  NOT NULL,
    estado                   VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    monto                    NUMERIC(18,2) NOT NULL,
    cuenta_debito_sugerida   VARCHAR(20),
    cuenta_credito_sugerida  VARCHAR(20),
    comprobante_ajuste_id    BIGINT,
    descripcion              VARCHAR(500),
    created_at               TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at               TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_partidas_conc_company   ON partidas_conciliatorias (company_id);
CREATE INDEX IF NOT EXISTS idx_partidas_conc_cuenta    ON partidas_conciliatorias (bank_account_id);
CREATE INDEX IF NOT EXISTS idx_partidas_conc_mov       ON partidas_conciliatorias (financial_movement_id);
CREATE INDEX IF NOT EXISTS idx_partidas_conc_estado    ON partidas_conciliatorias (estado);
-- Una partida activa por movimiento (evita duplicar al re-pre-procesar).
CREATE UNIQUE INDEX IF NOT EXISTS uk_partidas_conc_mov_activa
    ON partidas_conciliatorias (financial_movement_id) WHERE deleted_at IS NULL;

-- ----- Menús de UI -----
DO $$
DECLARE v_bnk BIGINT;
BEGIN
    SELECT id INTO v_bnk FROM modules WHERE name = 'Bancos y Cajas' AND deleted_at IS NULL LIMIT 1;
    IF v_bnk IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, required_permission_code, created_at, updated_at)
        SELECT 'Partidas Conciliatorias', 'ri-scales-3-line', 'partidas-conciliatorias', 18, v_bnk, 'ACTIVE', 'PARTIDAS_CONCILIATORIAS', true, 'BNK.CUENTAS.VER', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'PARTIDAS_CONCILIATORIAS' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, required_permission_code, created_at, updated_at)
        SELECT 'GMF (4x1000)', 'ri-percent-line', 'gmf', 19, v_bnk, 'ACTIVE', 'GMF', true, 'BNK.CUENTAS.VER', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'GMF' AND deleted_at IS NULL);
    END IF;
END $$;
