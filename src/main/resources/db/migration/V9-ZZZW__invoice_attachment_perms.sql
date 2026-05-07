-- QA Bloque AU+ HU-AP-12 E10 (2026-05-07)
-- =============================================================================
-- Permisos para adjuntos de factura de compra: el QA reporto que no existian
-- en la UI los toggles para habilitar/quitar permisos de adjuntar/reemplazar/
-- eliminar archivos en facturas. Los permisos VIEW/CREATE/DELETE existian
-- pero con nombres "(legacy)" no legibles, sin permiso UPDATE para reemplazar
-- y sin asignacion a los roles funcionales (CONTADOR/AUDITOR).
--
-- Esta migracion:
--   1) Renombra los 3 permisos existentes con nombres legibles en espaniol.
--   2) Crea PERM UPDATE_INVOICE_ATTACHMENT para reemplazo (HU-AP-12 E4).
--   3) Asigna los permisos a los roles correspondientes:
--      - CONTADOR (1)            : VIEW + CREATE + UPDATE + DELETE
--      - AUXILIAR_CONTABLE (2)   : VIEW + CREATE + UPDATE
--      - AUDITOR (3)             : VIEW (solo lectura)
--      - ADMIN_EMPRESA (4)       : ya tiene bypass via authority de rol
-- =============================================================================

DO $$
DECLARE
    v_module_id BIGINT;
    v_perm_view BIGINT;
    v_perm_create BIGINT;
    v_perm_update BIGINT;
    v_perm_delete BIGINT;
BEGIN
    SELECT id INTO v_module_id FROM modules
        WHERE name = 'Cuentas por Pagar' AND deleted_at IS NULL LIMIT 1;
    IF v_module_id IS NULL THEN
        RAISE NOTICE 'V9-ZZZW: modulo Cuentas por Pagar no existe, abort';
        RETURN;
    END IF;

    -- 1) Renombrar permisos existentes (mantener mismo code)
    UPDATE permissions
       SET name = 'Ver adjuntos de factura de compra',
           description = 'Permite consultar y descargar documentos soporte adjuntos a facturas de compra (HU-AP-12 E10).',
           updated_at = NOW()
     WHERE code = 'VIEW_INVOICE_ATTACHMENT' AND deleted_at IS NULL;

    UPDATE permissions
       SET name = 'Adjuntar documento a factura de compra',
           description = 'Permite subir un documento soporte (PDF/XML/JPG/PNG) a una factura de compra (HU-AP-12 E1/E10).',
           updated_at = NOW()
     WHERE code = 'CREATE_INVOICE_ATTACHMENT' AND deleted_at IS NULL;

    UPDATE permissions
       SET name = 'Eliminar adjunto de factura de compra',
           description = 'Permite eliminar logicamente un documento soporte de factura de compra (HU-AP-12 E10).',
           updated_at = NOW()
     WHERE code = 'DELETE_INVOICE_ATTACHMENT' AND deleted_at IS NULL;

    -- 2) Crear PERM UPDATE para reemplazo (HU-AP-12 E4)
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_INVOICE_ATTACHMENT' AND deleted_at IS NULL) THEN
        INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
        VALUES ('UPDATE_INVOICE_ATTACHMENT',
                'Reemplazar adjunto de factura de compra',
                'Permite reemplazar un documento soporte por una nueva version, manteniendo el historial (HU-AP-12 E4/E10).',
                'UPDATE', v_module_id, NOW(), NOW());
    END IF;

    -- 3) Resolver IDs
    SELECT id INTO v_perm_view   FROM permissions WHERE code = 'VIEW_INVOICE_ATTACHMENT' AND deleted_at IS NULL;
    SELECT id INTO v_perm_create FROM permissions WHERE code = 'CREATE_INVOICE_ATTACHMENT' AND deleted_at IS NULL;
    SELECT id INTO v_perm_update FROM permissions WHERE code = 'UPDATE_INVOICE_ATTACHMENT' AND deleted_at IS NULL;
    SELECT id INTO v_perm_delete FROM permissions WHERE code = 'DELETE_INVOICE_ATTACHMENT' AND deleted_at IS NULL;

    -- 4) Asignar a roles - usar INSERT ... ON CONFLICT DO NOTHING idempotente
    -- CONTADOR (1): los 4
    INSERT INTO roles_permissions (role_id, permission_id)
    SELECT 1, p FROM (VALUES (v_perm_view), (v_perm_create), (v_perm_update), (v_perm_delete)) AS t(p)
    WHERE p IS NOT NULL
    ON CONFLICT DO NOTHING;

    -- AUXILIAR_CONTABLE (2): VIEW + CREATE + UPDATE
    INSERT INTO roles_permissions (role_id, permission_id)
    SELECT 2, p FROM (VALUES (v_perm_view), (v_perm_create), (v_perm_update)) AS t(p)
    WHERE p IS NOT NULL
    ON CONFLICT DO NOTHING;

    -- AUDITOR (3): solo VIEW
    IF v_perm_view IS NOT NULL THEN
        INSERT INTO roles_permissions (role_id, permission_id)
        VALUES (3, v_perm_view)
        ON CONFLICT DO NOTHING;
    END IF;

    RAISE NOTICE 'V9-ZZZW OK: 4 permisos invoice_attachment renombrados/creados y asignados a roles';
END $$;

-- =============================================================================
-- HU-AP-03 E1 (2026-05-07): suscripciones default al evento AP_INVOICE_SETTLED
-- para que CONTADOR y ADMIN_EMPRESA reciban notificacion in-app cuando una
-- factura de compra se liquida (deuda saldada). companyId=NULL = aplica a
-- todas las empresas (default global). El admin puede personalizar luego.
-- =============================================================================
INSERT INTO role_notification_subscriptions (role_id, event_key, enabled, company_id, created_at, updated_at)
SELECT r.id, 'AP_INVOICE_SETTLED', true, NULL, NOW(), NOW()
FROM roles r
WHERE r.name IN ('CONTADOR', 'ADMIN_EMPRESA') AND r.deleted_at IS NULL
ON CONFLICT DO NOTHING;

