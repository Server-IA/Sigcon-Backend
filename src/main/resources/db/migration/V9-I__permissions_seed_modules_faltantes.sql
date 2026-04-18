-- V9-I: seed de permisos para los 8 modulos que quedaron sin entradas en la tabla
-- `permissions`. Sin este seed el modal "Editar Rol" solo muestra Parametrizacion,
-- Listas Contables y Bancos y Cajas, y los administradores no pueden otorgar
-- permisos granulares para los modulos restantes (ACT, TER, AP, AR, CG, INT, NOM, AU).
--
-- Cada modulo recibe 4 permisos base (VIEW/CREATE/UPDATE/DELETE). Los @PreAuthorize
-- de los controllers ya verifican codigos PERM_* equivalentes + fallback ROLE_ADMIN,
-- asi que estos permisos habilitan granularidad real sin romper ADMIN.
--
-- Idempotente: usa WHERE NOT EXISTS sobre code (UNIQUE).

-- =============================================================================
-- Modulo 3: Activos
-- =============================================================================
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_VIEW_ASSETS', 'Ver activos', 'Permiso para consultar activos fijos', 'READ', 3, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_VIEW_ASSETS');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_CREATE_ASSETS', 'Crear activos', 'Permiso para registrar activos fijos', 'CREATE', 3, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_CREATE_ASSETS');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_UPDATE_ASSETS', 'Actualizar activos', 'Permiso para modificar activos fijos', 'UPDATE', 3, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_UPDATE_ASSETS');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_DELETE_ASSETS', 'Eliminar activos', 'Permiso para eliminar activos fijos', 'DELETE', 3, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_DELETE_ASSETS');

-- =============================================================================
-- Modulo 4: Terceros
-- =============================================================================
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_VIEW_THIRD_PARTIES', 'Ver terceros', 'Permiso para consultar terceros', 'READ', 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_VIEW_THIRD_PARTIES');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_CREATE_THIRD_PARTIES', 'Crear terceros', 'Permiso para registrar terceros', 'CREATE', 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_CREATE_THIRD_PARTIES');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_UPDATE_THIRD_PARTIES', 'Actualizar terceros', 'Permiso para modificar terceros', 'UPDATE', 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_UPDATE_THIRD_PARTIES');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_DELETE_THIRD_PARTIES', 'Eliminar terceros', 'Permiso para eliminar terceros', 'DELETE', 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_DELETE_THIRD_PARTIES');

-- =============================================================================
-- Modulo 6: Cuentas por Pagar
-- =============================================================================
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_VIEW_ACCOUNTS_PAYABLE', 'Ver cuentas por pagar', 'Permiso para consultar cuentas por pagar', 'READ', 6, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_VIEW_ACCOUNTS_PAYABLE');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_CREATE_ACCOUNTS_PAYABLE', 'Crear cuentas por pagar', 'Permiso para registrar facturas, pagos y anticipos AP', 'CREATE', 6, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_CREATE_ACCOUNTS_PAYABLE');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_UPDATE_ACCOUNTS_PAYABLE', 'Actualizar cuentas por pagar', 'Permiso para modificar registros AP', 'UPDATE', 6, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_UPDATE_ACCOUNTS_PAYABLE');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_DELETE_ACCOUNTS_PAYABLE', 'Eliminar cuentas por pagar', 'Permiso para eliminar registros AP', 'DELETE', 6, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_DELETE_ACCOUNTS_PAYABLE');

-- =============================================================================
-- Modulo 7: Contabilidad General
-- =============================================================================
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_VIEW_ACCOUNTING', 'Ver contabilidad', 'Permiso para consultar comprobantes, libros y estados financieros', 'READ', 7, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_VIEW_ACCOUNTING');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_CREATE_ACCOUNTING', 'Crear registros contables', 'Permiso para crear comprobantes y asientos', 'CREATE', 7, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_CREATE_ACCOUNTING');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_UPDATE_ACCOUNTING', 'Actualizar registros contables', 'Permiso para modificar comprobantes en borrador', 'UPDATE', 7, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_UPDATE_ACCOUNTING');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_DELETE_ACCOUNTING', 'Eliminar registros contables', 'Permiso para eliminar comprobantes en borrador', 'DELETE', 7, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_DELETE_ACCOUNTING');

-- =============================================================================
-- Modulo 8: Cuentas por Cobrar
-- =============================================================================
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_VIEW_ACCOUNTS_RECEIVABLE', 'Ver cuentas por cobrar', 'Permiso para consultar facturas de venta, cobros y cartera', 'READ', 8, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_VIEW_ACCOUNTS_RECEIVABLE');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_CREATE_ACCOUNTS_RECEIVABLE', 'Crear cuentas por cobrar', 'Permiso para registrar facturas de venta, cobros y anticipos AR', 'CREATE', 8, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_CREATE_ACCOUNTS_RECEIVABLE');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_UPDATE_ACCOUNTS_RECEIVABLE', 'Actualizar cuentas por cobrar', 'Permiso para modificar registros AR', 'UPDATE', 8, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_UPDATE_ACCOUNTS_RECEIVABLE');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_DELETE_ACCOUNTS_RECEIVABLE', 'Eliminar cuentas por cobrar', 'Permiso para eliminar registros AR', 'DELETE', 8, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_DELETE_ACCOUNTS_RECEIVABLE');

-- =============================================================================
-- Modulo 9: Integracion AAEF
-- =============================================================================
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_VIEW_INTEGRATION', 'Ver integracion AAEF', 'Permiso para consultar lotes y transferencias AAEF', 'READ', 9, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_VIEW_INTEGRATION');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_CREATE_INTEGRATION', 'Crear integracion AAEF', 'Permiso para reintentar transferencias AAEF', 'CREATE', 9, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_CREATE_INTEGRATION');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_UPDATE_INTEGRATION', 'Actualizar integracion AAEF', 'Permiso para modificar parametros AAEF', 'UPDATE', 9, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_UPDATE_INTEGRATION');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_DELETE_INTEGRATION', 'Eliminar integracion AAEF', 'Permiso para eliminar registros AAEF', 'DELETE', 9, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_DELETE_INTEGRATION');

-- =============================================================================
-- Modulo 10: Nomina
-- =============================================================================
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_VIEW_PAYROLL', 'Ver nomina', 'Permiso para consultar empleados, conceptos y recibos de nomina', 'READ', 10, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_VIEW_PAYROLL');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_CREATE_PAYROLL', 'Crear nomina', 'Permiso para registrar empleados y liquidar recibos', 'CREATE', 10, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_CREATE_PAYROLL');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_UPDATE_PAYROLL', 'Actualizar nomina', 'Permiso para modificar empleados y conceptos de nomina', 'UPDATE', 10, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_UPDATE_PAYROLL');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_DELETE_PAYROLL', 'Eliminar nomina', 'Permiso para eliminar empleados y recibos en borrador', 'DELETE', 10, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_DELETE_PAYROLL');

-- =============================================================================
-- Modulo 11: Auditoria
-- =============================================================================
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_VIEW_AUDIT', 'Ver auditoria', 'Permiso para consultar logs, dashboard y evidencias de auditoria', 'READ', 11, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_VIEW_AUDIT');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_CREATE_AUDIT', 'Crear politicas de auditoria', 'Permiso para configurar reglas de riesgo y retencion', 'CREATE', 11, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_CREATE_AUDIT');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_UPDATE_AUDIT', 'Actualizar politicas de auditoria', 'Permiso para modificar reglas de riesgo y retencion', 'UPDATE', 11, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_UPDATE_AUDIT');

INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'PERM_DELETE_AUDIT', 'Eliminar politicas de auditoria', 'Permiso para eliminar politicas (los logs NO son eliminables por diseño)', 'DELETE', 11, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PERM_DELETE_AUDIT');
