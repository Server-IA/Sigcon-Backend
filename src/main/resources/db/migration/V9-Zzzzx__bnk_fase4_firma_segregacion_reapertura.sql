-- =====================================================================
-- BNK FASE 4 (HU-066/067/075/077): firma electrónica + segregación de
-- funciones + reapertura/versionado + informe PDF firmado.
-- =====================================================================
-- Las tablas (sesiones_conciliacion, firmas_electronicas, solicitudes_reapertura,
-- bnk_config_firma) las crea Hibernate ddl-auto desde las entidades ANTES de esta
-- migración. Aquí: roles nuevos por empresa, config de firma por empresa, índices
-- y menús. Todo idempotente y aditivo (R2/R3). Prefijo 'x' minúscula => ordena
-- después de V9-Z__multi.
-- =====================================================================

-- ----- 1. Roles nuevos por empresa (HU-066/067): REVISOR_FISCAL, SUPERVISOR_CONCILIACION, CONCILIADOR
INSERT INTO roles (company_id, name, description, status, version, is_predefined, created_at, updated_at)
SELECT c.id, v.rname, v.rdesc, 'ACTIVE', 0, true, NOW(), NOW()
FROM companies c
CROSS JOIN (VALUES
    ('REVISOR_FISCAL', 'Revisor fiscal: aprueba y firma conciliaciones (segregación de funciones)'),
    ('SUPERVISOR_CONCILIACION', 'Supervisor de conciliación: revisa y aprueba'),
    ('CONCILIADOR', 'Conciliador: elabora y envía a revisión')
) AS v(rname, rdesc)
WHERE c.deleted_at IS NULL AND c.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM roles r WHERE r.company_id = c.id AND r.name = v.rname AND r.deleted_at IS NULL);

-- ----- 2. Config de firma por empresa (HU-066 E1): por defecto OTP, modo ESTRICTO
INSERT INTO bnk_config_firma (company_id, metodos_permitidos, exige_cert_revisor, modo_flexible, created_at, updated_at)
SELECT c.id, 'OTP', false, false, NOW(), NOW()
FROM companies c
WHERE c.deleted_at IS NULL AND c.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM bnk_config_firma f WHERE f.company_id = c.id);

-- ----- 3. Índices de las tablas nuevas
CREATE INDEX IF NOT EXISTS idx_sesion_conc_company ON sesiones_conciliacion (company_id);
CREATE INDEX IF NOT EXISTS idx_sesion_conc_cuenta  ON sesiones_conciliacion (bank_account_id);
CREATE INDEX IF NOT EXISTS idx_sesion_conc_estado  ON sesiones_conciliacion (estado);
CREATE INDEX IF NOT EXISTS idx_sesion_conc_origen  ON sesiones_conciliacion (sesion_origen_id);
CREATE INDEX IF NOT EXISTS idx_firma_elec_sesion   ON firmas_electronicas (sesion_id);
CREATE INDEX IF NOT EXISTS idx_solic_reap_sesion   ON solicitudes_reapertura (sesion_id);
CREATE INDEX IF NOT EXISTS idx_solic_reap_estado   ON solicitudes_reapertura (estado);

-- ----- 4. Menús de UI (Bancos y Cajas)
DO $$
DECLARE v_bnk BIGINT;
BEGIN
    SELECT id INTO v_bnk FROM modules WHERE name = 'Bancos y Cajas' AND deleted_at IS NULL LIMIT 1;
    IF v_bnk IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, required_permission_code, created_at, updated_at)
        SELECT 'Cierre y Firma', 'ri-quill-pen-line', 'sesiones-conciliacion', 20, v_bnk, 'ACTIVE', 'SESIONES_FIRMA', true, 'BNK.CUENTAS.VER', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'SESIONES_FIRMA' AND deleted_at IS NULL);

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, required_permission_code, created_at, updated_at)
        SELECT 'Config. de Firma', 'ri-shield-keyhole-line', 'config-firma', 21, v_bnk, 'ACTIVE', 'CONFIG_FIRMA', true, 'BNK.CUENTAS.VER', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'CONFIG_FIRMA' AND deleted_at IS NULL);
    END IF;
END $$;
