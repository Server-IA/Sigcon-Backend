-- V9-ZZZS — QA Bloque AU+ HU-AP-12 E10 (2026-05-06)
--
-- Crea los 3 permisos granulares para gestionar adjuntos en facturas de compra.
-- El controller (`InvoiceAttachmentController`) ya los referencia en sus
-- @PreAuthorize:
--   - PERM_VIEW_INVOICE_ATTACHMENT  (listar + descargar)
--   - PERM_CREATE_INVOICE_ATTACHMENT (subir + reemplazar v2)
--   - PERM_DELETE_INVOICE_ATTACHMENT (eliminar)
--
-- Antes los endpoints chequeaban contra esos permisos pero NO existian en la
-- tabla `permissions`, asi que el modal "Editar Rol" no los listaba y el QA
-- no podia probar HU-AP-12 E10 (usuario sin permiso intentando adjuntar).
--
-- Idempotente: WHERE NOT EXISTS por code; asignacion a rol via NOT EXISTS en
-- (role_id, permission_id).
--
-- module_id = 6 (modulo "Cuentas por Pagar"). Si tu instancia usa otro id,
-- el script lo resuelve dinamicamente desde la tabla modules.

DO $$
DECLARE
    v_module_id BIGINT;
BEGIN
    -- Resolver module_id de "Cuentas por Pagar" (puede variar por seed)
    SELECT id INTO v_module_id FROM modules
     WHERE name IN ('Cuentas por Pagar','Cuentas por pagar','Accounts Payable')
       AND deleted_at IS NULL
     LIMIT 1;

    IF v_module_id IS NULL THEN
        RAISE NOTICE 'V9-ZZZS: modulo Cuentas por Pagar no encontrado, usando id=6 por convencion';
        v_module_id := 6;
    END IF;

    -- 1) Crear los 3 permisos si no existen
    INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
    SELECT 'VIEW_INVOICE_ATTACHMENT',
           'Ver adjuntos de factura de compra',
           'Permite consultar y descargar documentos soporte de facturas AP',
           'READ', v_module_id, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_INVOICE_ATTACHMENT' AND deleted_at IS NULL);

    INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
    SELECT 'CREATE_INVOICE_ATTACHMENT',
           'Adjuntar archivos a factura de compra',
           'Permite subir y reemplazar (v2) documentos soporte de facturas AP',
           'CREATE', v_module_id, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_INVOICE_ATTACHMENT' AND deleted_at IS NULL);

    INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
    SELECT 'DELETE_INVOICE_ATTACHMENT',
           'Eliminar adjuntos de factura de compra',
           'Permite eliminar logicamente documentos soporte de facturas AP',
           'DELETE', v_module_id, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_INVOICE_ATTACHMENT' AND deleted_at IS NULL);

    -- 2) Asignar al rol CONTADOR (operacion completa)
    INSERT INTO roles_permissions (role_id, permission_id)
    SELECT r.id, p.id
      FROM roles r, permissions p
     WHERE r.name = 'CONTADOR' AND r.deleted_at IS NULL
       AND p.code IN ('VIEW_INVOICE_ATTACHMENT','CREATE_INVOICE_ATTACHMENT','DELETE_INVOICE_ATTACHMENT')
       AND p.deleted_at IS NULL
       AND NOT EXISTS (
           SELECT 1 FROM roles_permissions rp
            WHERE rp.role_id = r.id AND rp.permission_id = p.id
       );

    -- 3) Asignar al rol AUDITOR solo VIEW
    INSERT INTO roles_permissions (role_id, permission_id)
    SELECT r.id, p.id
      FROM roles r, permissions p
     WHERE r.name = 'AUDITOR' AND r.deleted_at IS NULL
       AND p.code = 'VIEW_INVOICE_ATTACHMENT'
       AND p.deleted_at IS NULL
       AND NOT EXISTS (
           SELECT 1 FROM roles_permissions rp
            WHERE rp.role_id = r.id AND rp.permission_id = p.id
       );

    -- 4) Asignar al rol AUXILIAR_CONTABLE: VIEW + CREATE (no DELETE)
    INSERT INTO roles_permissions (role_id, permission_id)
    SELECT r.id, p.id
      FROM roles r, permissions p
     WHERE r.name = 'AUXILIAR_CONTABLE' AND r.deleted_at IS NULL
       AND p.code IN ('VIEW_INVOICE_ATTACHMENT','CREATE_INVOICE_ATTACHMENT')
       AND p.deleted_at IS NULL
       AND NOT EXISTS (
           SELECT 1 FROM roles_permissions rp
            WHERE rp.role_id = r.id AND rp.permission_id = p.id
       );

    RAISE NOTICE 'V9-ZZZS: permisos VIEW/CREATE/DELETE_INVOICE_ATTACHMENT asignados a CONTADOR/AUDITOR/AUXILIAR_CONTABLE';
END $$;
