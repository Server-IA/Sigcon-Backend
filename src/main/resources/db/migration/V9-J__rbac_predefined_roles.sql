-- V9-J: RBAC pragmatico para SIGCON.
--
-- Arregla 3 problemas estructurales del sistema de permisos:
--   1. Los codes en BD a veces traen el prefijo PERM_ duplicado (ej.
--      PERM_VIEW_BANK_ACCOUNT). User.java:107 agrega otro PERM_ en runtime y
--      el authority final no coincide con el @PreAuthorize del controller.
--      Solo ROLE_ADMIN pasaba por fallback.
--   2. Faltaban 114 permisos granulares que los controllers buscan pero
--      nunca se seedaron.
--   3. No existian roles predefinidos: un admin tenia que construir roles
--      desde cero.
--
-- Esta migracion:
--   1) Soft-delete de permisos huerfanos/bugueados.
--   2) Insert de los 114 permisos faltantes (sin prefix PERM_).
--   3) Crea roles CONTADOR / AUXILIAR_CONTABLE / AUDITOR.
--   4) Asigna permisos a cada rol.
--
-- Idempotente (WHERE NOT EXISTS). ADMIN no se ve afectado.

-- ============================================================================
-- 1. Soft-delete de permisos huerfanos o con doble prefix
-- ============================================================================
-- Nota: se omite cleanup de VIEW_COMPANY/CREATE_COMPANY/... porque V14
-- (script legado del DataInitializer) los reinserta en cada arranque y
-- colisionaria con el unique constraint. Son inofensivos (modulo Empresa
-- fue eliminado en Fase 0 - ningun controller los verifica).
UPDATE permissions SET deleted_at = NOW()
 WHERE deleted_at IS NULL
   AND code IN (
     'PERM_CREATE_ACCOUNTING',
     'PERM_CREATE_ACCOUNTS_PAYABLE',
     'PERM_CREATE_ACCOUNTS_RECEIVABLE',
     'PERM_CREATE_ASSETS',
     'PERM_CREATE_AUDIT',
     'PERM_CREATE_INTEGRATION',
     'PERM_CREATE_PAYROLL',
     'PERM_CREATE_THIRD_PARTIES',
     'PERM_UPDATE_ACCOUNTING',
     'PERM_UPDATE_ACCOUNTS_PAYABLE',
     'PERM_UPDATE_ACCOUNTS_RECEIVABLE',
     'PERM_UPDATE_ASSETS',
     'PERM_UPDATE_AUDIT',
     'PERM_UPDATE_INTEGRATION',
     'PERM_UPDATE_PAYROLL',
     'PERM_UPDATE_THIRD_PARTIES',
     'PERM_DELETE_ACCOUNTING',
     'PERM_DELETE_ACCOUNTS_PAYABLE',
     'PERM_DELETE_ACCOUNTS_RECEIVABLE',
     'PERM_DELETE_ASSETS',
     'PERM_DELETE_AUDIT',
     'PERM_DELETE_INTEGRATION',
     'PERM_DELETE_PAYROLL',
     'PERM_DELETE_THIRD_PARTIES',
     'PERM_VIEW_ACCOUNTING',
     'PERM_VIEW_ACCOUNTS_PAYABLE',
     'PERM_VIEW_ACCOUNTS_RECEIVABLE',
     'PERM_VIEW_ASSETS',
     'PERM_VIEW_AUDIT',
     'PERM_VIEW_INTEGRATION',
     'PERM_VIEW_PAYROLL',
     'PERM_VIEW_THIRD_PARTIES',
     'PERM_CREATE_BANK_ACCOUNT',
     'PERM_DELETE_BANK_ACCOUNT',
     'PERM_UPDATE_BANK_ACCOUNT',
     'PERM_VIEW_BANK_ACCOUNT'
   );

-- ============================================================================
-- 2. Insert de los 114 permisos granulares faltantes
-- ============================================================================
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'ADJUST_ECL_SEGMENT', 'Adjust ecl segment', 'Permiso para adjust ecl segment', 'CREATE', 4, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'ADJUST_ECL_SEGMENT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'APPROVE_PURCHASE_ORDER', 'Approve purchase order', 'Permiso para approve purchase order', 'CREATE', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'APPROVE_PURCHASE_ORDER' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'ASSIGN_ACCOUNTING_ACCOUNT_TO_RULER_TAX', 'Assign accounting account to ruler tax', 'Permiso para assign accounting account to ruler tax', 'CREATE', 2, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'ASSIGN_ACCOUNTING_ACCOUNT_TO_RULER_TAX' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'BULK_STORE_THIRD_PARTY', 'Bulk store third party', 'Permiso para bulk store third party', 'CREATE', 4, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'BULK_STORE_THIRD_PARTY' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CALCULATE_ECL_SEGMENT', 'Calculate ecl segment', 'Permiso para calculate ecl segment', 'CREATE', 4, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CALCULATE_ECL_SEGMENT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CHANGE_CASH_STATUS', 'Change cash status', 'Permiso para change cash status', 'CREATE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CHANGE_CASH_STATUS' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_AP_ADVANCE', 'Create ap advance', 'Permiso para create ap advance', 'CREATE', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_AP_ADVANCE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_AP_NOTE', 'Create ap note', 'Permiso para create ap note', 'CREATE', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_AP_NOTE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_AP_PAYMENT', 'Create ap payment', 'Permiso para create ap payment', 'CREATE', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_AP_PAYMENT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_AR_ADVANCE', 'Create ar advance', 'Permiso para create ar advance', 'CREATE', 8, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_AR_ADVANCE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_AR_NOTE', 'Create ar note', 'Permiso para create ar note', 'CREATE', 8, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_AR_NOTE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_AR_PAYMENT', 'Create ar payment', 'Permiso para create ar payment', 'CREATE', 8, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_AR_PAYMENT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_ASSET', 'Create asset', 'Permiso para create asset', 'CREATE', 3, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_ASSET' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_BANK', 'Create bank', 'Permiso para create bank', 'CREATE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_BANK' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_BANK_ACCOUNT', 'Create bank account', 'Permiso para create bank account', 'CREATE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_BANK_ACCOUNT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_BANK_BRANCH', 'Create bank branch', 'Permiso para create bank branch', 'CREATE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_BANK_BRANCH' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_BANK_CHECK', 'Create bank check', 'Permiso para create bank check', 'CREATE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_BANK_CHECK' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_CASH', 'Create cash', 'Permiso para create cash', 'CREATE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_CASH' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_CHECKBOOK', 'Create checkbook', 'Permiso para create checkbook', 'CREATE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_CHECKBOOK' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_COMMERCIAL_DATA', 'Create commercial data', 'Permiso para create commercial data', 'CREATE', 4, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_COMMERCIAL_DATA' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_DIAN_RESOLUTION', 'Create dian resolution', 'Permiso para create dian resolution', 'CREATE', 8, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_DIAN_RESOLUTION' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_DIAN_XML', 'Create dian xml', 'Permiso para create dian xml', 'CREATE', 8, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_DIAN_XML' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_EXCHANGE_RATES', 'Create exchange rates', 'Permiso para create exchange rates', 'CREATE', 2, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_EXCHANGE_RATES' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_GOODS_RECEIPT', 'Create goods receipt', 'Permiso para create goods receipt', 'CREATE', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_GOODS_RECEIPT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_INVOICE_FC', 'Create invoice fc', 'Permiso para create invoice fc', 'CREATE', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_INVOICE_FC' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_PURCHASE_ORDER', 'Create purchase order', 'Permiso para create purchase order', 'CREATE', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_PURCHASE_ORDER' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_REPORT_TEMPLATES', 'Create report templates', 'Permiso para create report templates', 'CREATE', 1, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_REPORT_TEMPLATES' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_REPORT_TYPES', 'Create report types', 'Permiso para create report types', 'CREATE', 1, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_REPORT_TYPES' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_RULER_TAX', 'Create ruler tax', 'Permiso para create ruler tax', 'CREATE', 2, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_RULER_TAX' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_SALES_INVOICE', 'Create sales invoice', 'Permiso para create sales invoice', 'CREATE', 8, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_SALES_INVOICE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_THIRD_PARTIES', 'Create third parties', 'Permiso para create third parties', 'CREATE', 1, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_THIRD_PARTIES' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'CREATE_THIRD_PARTY', 'Create third party', 'Permiso para create third party', 'CREATE', 4, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CREATE_THIRD_PARTY' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_AP_INVOICE', 'Delete ap invoice', 'Permiso para delete ap invoice', 'DELETE', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_AP_INVOICE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_ASSET', 'Delete asset', 'Permiso para delete asset', 'DELETE', 3, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_ASSET' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_BANK', 'Delete bank', 'Permiso para delete bank', 'DELETE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_BANK' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_BANK_ACCOUNT', 'Delete bank account', 'Permiso para delete bank account', 'DELETE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_BANK_ACCOUNT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_BANK_BRANCH', 'Delete bank branch', 'Permiso para delete bank branch', 'DELETE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_BANK_BRANCH' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_BANK_CHECK', 'Delete bank check', 'Permiso para delete bank check', 'DELETE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_BANK_CHECK' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_CASH', 'Delete cash', 'Permiso para delete cash', 'DELETE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_CASH' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_CHECKBOOK', 'Delete checkbook', 'Permiso para delete checkbook', 'DELETE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_CHECKBOOK' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_COMMERCIAL_DATA', 'Delete commercial data', 'Permiso para delete commercial data', 'DELETE', 4, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_COMMERCIAL_DATA' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_DIAN_RESOLUTION', 'Delete dian resolution', 'Permiso para delete dian resolution', 'DELETE', 8, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_DIAN_RESOLUTION' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_EXCHANGE_RATES', 'Delete exchange rates', 'Permiso para delete exchange rates', 'DELETE', 2, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_EXCHANGE_RATES' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_INVOICE', 'Delete invoice', 'Permiso para delete invoice', 'DELETE', 1, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_INVOICE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_PERMISSION', 'Delete permission', 'Permiso para delete permission', 'DELETE', 1, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_PERMISSION' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_PURCHASE_ORDER', 'Delete purchase order', 'Permiso para delete purchase order', 'DELETE', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_PURCHASE_ORDER' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_REPORT_TEMPLATES', 'Delete report templates', 'Permiso para delete report templates', 'DELETE', 1, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_REPORT_TEMPLATES' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_REPORT_TYPES', 'Delete report types', 'Permiso para delete report types', 'DELETE', 1, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_REPORT_TYPES' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_RULER_TAX', 'Delete ruler tax', 'Permiso para delete ruler tax', 'DELETE', 2, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_RULER_TAX' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_SALES_INVOICE', 'Delete sales invoice', 'Permiso para delete sales invoice', 'DELETE', 8, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_SALES_INVOICE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_THIRD_PARTIES', 'Delete third parties', 'Permiso para delete third parties', 'DELETE', 1, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_THIRD_PARTIES' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'DELETE_THIRD_PARTY', 'Delete third party', 'Permiso para delete third party', 'DELETE', 4, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'DELETE_THIRD_PARTY' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'EXPORT_THIRD_PARTY', 'Export third party', 'Permiso para export third party', 'CREATE', 4, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'EXPORT_THIRD_PARTY' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'MANAGE_THIRD_PARTY_ROLES_STATUS', 'Manage third party roles status', 'Permiso para manage third party roles status', 'CREATE', 4, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'MANAGE_THIRD_PARTY_ROLES_STATUS' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'READ_AP_ADVANCE', 'Read ap advance', 'Permiso para read ap advance', 'READ', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'READ_AP_ADVANCE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'READ_AP_INVOICE', 'Read ap invoice', 'Permiso para read ap invoice', 'READ', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'READ_AP_INVOICE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'READ_AP_NOTE', 'Read ap note', 'Permiso para read ap note', 'READ', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'READ_AP_NOTE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'READ_AP_PAYMENT', 'Read ap payment', 'Permiso para read ap payment', 'READ', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'READ_AP_PAYMENT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'READ_AP_REPORT', 'Read ap report', 'Permiso para read ap report', 'READ', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'READ_AP_REPORT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'READ_AR_ADVANCE', 'Read ar advance', 'Permiso para read ar advance', 'READ', 8, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'READ_AR_ADVANCE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'READ_AR_NOTE', 'Read ar note', 'Permiso para read ar note', 'READ', 8, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'READ_AR_NOTE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'READ_AR_PAYMENT', 'Read ar payment', 'Permiso para read ar payment', 'READ', 8, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'READ_AR_PAYMENT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'READ_DIAN', 'Read dian', 'Permiso para read dian', 'READ', 8, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'READ_DIAN' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'READ_DIAN_REPORT', 'Read dian report', 'Permiso para read dian report', 'READ', 8, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'READ_DIAN_REPORT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'READ_DIAN_RESOLUTION', 'Read dian resolution', 'Permiso para read dian resolution', 'READ', 8, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'READ_DIAN_RESOLUTION' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'READ_GOODS_RECEIPT', 'Read goods receipt', 'Permiso para read goods receipt', 'READ', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'READ_GOODS_RECEIPT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'READ_INVOICE', 'Read invoice', 'Permiso para read invoice', 'READ', 1, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'READ_INVOICE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'READ_PURCHASE_ORDER', 'Read purchase order', 'Permiso para read purchase order', 'READ', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'READ_PURCHASE_ORDER' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'READ_SALES_INVOICE', 'Read sales invoice', 'Permiso para read sales invoice', 'READ', 8, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'READ_SALES_INVOICE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'RECONCILE_BANK_CHECK', 'Reconcile bank check', 'Permiso para reconcile bank check', 'CREATE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'RECONCILE_BANK_CHECK' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'REPORT_LOST_BANK_CHECK', 'Report lost bank check', 'Permiso para report lost bank check', 'CREATE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'REPORT_LOST_BANK_CHECK' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'SEARCH_VOUCHER', 'Search voucher', 'Permiso para search voucher', 'CREATE', 7, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'SEARCH_VOUCHER' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'SUBMIT_DIAN', 'Submit dian', 'Permiso para submit dian', 'CREATE', 8, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'SUBMIT_DIAN' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'UPDATE_AP_INVOICE', 'Update ap invoice', 'Permiso para update ap invoice', 'UPDATE', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_AP_INVOICE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'UPDATE_AP_PAYMENT', 'Update ap payment', 'Permiso para update ap payment', 'UPDATE', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_AP_PAYMENT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'UPDATE_ASSET', 'Update asset', 'Permiso para update asset', 'UPDATE', 3, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_ASSET' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'UPDATE_BANK', 'Update bank', 'Permiso para update bank', 'UPDATE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_BANK' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'UPDATE_BANK_ACCOUNT', 'Update bank account', 'Permiso para update bank account', 'UPDATE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_BANK_ACCOUNT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'UPDATE_BANK_BRANCH', 'Update bank branch', 'Permiso para update bank branch', 'UPDATE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_BANK_BRANCH' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'UPDATE_CASH', 'Update cash', 'Permiso para update cash', 'UPDATE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_CASH' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'UPDATE_CHECKBOOK', 'Update checkbook', 'Permiso para update checkbook', 'UPDATE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_CHECKBOOK' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'UPDATE_COMMERCIAL_DATA', 'Update commercial data', 'Permiso para update commercial data', 'UPDATE', 4, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_COMMERCIAL_DATA' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'UPDATE_DIAN_RESOLUTION', 'Update dian resolution', 'Permiso para update dian resolution', 'UPDATE', 8, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_DIAN_RESOLUTION' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'UPDATE_EXCHANGE_RATES', 'Update exchange rates', 'Permiso para update exchange rates', 'UPDATE', 2, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_EXCHANGE_RATES' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'UPDATE_GOODS_RECEIPT', 'Update goods receipt', 'Permiso para update goods receipt', 'UPDATE', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_GOODS_RECEIPT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'UPDATE_INVOICE', 'Update invoice', 'Permiso para update invoice', 'UPDATE', 1, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_INVOICE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'UPDATE_PURCHASE_ORDER', 'Update purchase order', 'Permiso para update purchase order', 'UPDATE', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_PURCHASE_ORDER' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'UPDATE_REPORT_TYPES', 'Update report types', 'Permiso para update report types', 'UPDATE', 1, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_REPORT_TYPES' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'UPDATE_RULER_TAX', 'Update ruler tax', 'Permiso para update ruler tax', 'UPDATE', 2, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_RULER_TAX' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'UPDATE_SALES_INVOICE', 'Update sales invoice', 'Permiso para update sales invoice', 'UPDATE', 8, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_SALES_INVOICE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'UPDATE_THIRD_PARTY', 'Update third party', 'Permiso para update third party', 'UPDATE', 4, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'UPDATE_THIRD_PARTY' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_AP_INVOICE', 'View ap invoice', 'Permiso para view ap invoice', 'READ', 6, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_AP_INVOICE' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_ASSET', 'View asset', 'Permiso para view asset', 'READ', 3, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_ASSET' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_BANK', 'View bank', 'Permiso para view bank', 'READ', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_BANK' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_BANK_ACCOUNT', 'View bank account', 'Permiso para view bank account', 'READ', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_BANK_ACCOUNT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_BANK_BRANCH', 'View bank branch', 'Permiso para view bank branch', 'READ', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_BANK_BRANCH' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_BANK_CHECK', 'View bank check', 'Permiso para view bank check', 'READ', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_BANK_CHECK' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_CASH', 'View cash', 'Permiso para view cash', 'READ', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_CASH' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_CHECKBOOK', 'View checkbook', 'Permiso para view checkbook', 'READ', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_CHECKBOOK' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_COMMERCIAL_DATA', 'View commercial data', 'Permiso para view commercial data', 'READ', 4, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_COMMERCIAL_DATA' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_ECL_SEGMENT', 'View ecl segment', 'Permiso para view ecl segment', 'READ', 4, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_ECL_SEGMENT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_EXCHANGE_RATES', 'View exchange rates', 'Permiso para view exchange rates', 'READ', 2, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_EXCHANGE_RATES' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_PAYMENT_FORM', 'View payment form', 'Permiso para view payment form', 'READ', 2, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_PAYMENT_FORM' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_PAYMENT_TERM', 'View payment term', 'Permiso para view payment term', 'READ', 2, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_PAYMENT_TERM' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_REPORT_TEMPLATES', 'View report templates', 'Permiso para view report templates', 'READ', 1, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_REPORT_TEMPLATES' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_REPORT_TYPES', 'View report types', 'Permiso para view report types', 'READ', 1, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_REPORT_TYPES' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_RULER_TAX', 'View ruler tax', 'Permiso para view ruler tax', 'READ', 2, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_RULER_TAX' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_TAX_REPORT', 'View tax report', 'Permiso para view tax report', 'READ', 1, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_TAX_REPORT' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_THIRD_PARTIES', 'View third parties', 'Permiso para view third parties', 'READ', 1, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_THIRD_PARTIES' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_THIRD_PARTY', 'View third party', 'Permiso para view third party', 'READ', 4, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_THIRD_PARTY' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_TYPES_ORGANIZATIONS', 'View types organizations', 'Permiso para view types organizations', 'READ', 2, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_TYPES_ORGANIZATIONS' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_TYPES_REGIMES', 'View types regimes', 'Permiso para view types regimes', 'READ', 2, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_TYPES_REGIMES' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VIEW_WITHHOLDINGS', 'View withholdings', 'Permiso para view withholdings', 'READ', 2, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VIEW_WITHHOLDINGS' AND deleted_at IS NULL);
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at) SELECT 'VOID_BANK_CHECK', 'Void bank check', 'Permiso para void bank check', 'CREATE', 5, NOW(), NOW() WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'VOID_BANK_CHECK' AND deleted_at IS NULL);

-- ============================================================================
-- 3. Roles predefinidos
-- ============================================================================
INSERT INTO roles (name, status, created_at, updated_at)
SELECT 'CONTADOR', 'ACTIVE', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'CONTADOR' AND deleted_at IS NULL);

INSERT INTO roles (name, status, created_at, updated_at)
SELECT 'AUXILIAR_CONTABLE', 'ACTIVE', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'AUXILIAR_CONTABLE' AND deleted_at IS NULL);

INSERT INTO roles (name, status, created_at, updated_at)
SELECT 'AUDITOR', 'ACTIVE', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'AUDITOR' AND deleted_at IS NULL);

-- ============================================================================
-- 4. Permisos del rol CONTADOR
-- ============================================================================

-- CONTADOR: operacion contable completa sin cerrar periodo ni parametrizar
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r CROSS JOIN permissions p
 WHERE r.name = 'CONTADOR' AND r.deleted_at IS NULL
   AND p.deleted_at IS NULL
   AND p.code IN (
     'VIEW_CHART_OF_ACCOUNT',
     'VIEW_ACCOUNTING_ACCOUNT',
     'VIEW_RULER_TAX',
     'VIEW_EXCHANGE_RATES',
     'VIEW_PAYMENT_FORM',
     'VIEW_PAYMENT_TERM',
     'VIEW_WITHHOLDINGS',
     'VIEW_TYPES_REGIMES',
     'VIEW_TYPES_ORGANIZATIONS',
     'VIEW_ASSET',
     'CREATE_ASSET',
     'UPDATE_ASSET',
     'VIEW_THIRD_PARTIES',
     'VIEW_THIRD_PARTY',
     'CREATE_THIRD_PARTY',
     'UPDATE_THIRD_PARTY',
     'BULK_STORE_THIRD_PARTY',
     'EXPORT_THIRD_PARTY',
     'VIEW_COMMERCIAL_DATA',
     'CREATE_COMMERCIAL_DATA',
     'UPDATE_COMMERCIAL_DATA',
     'VIEW_ECL_SEGMENT',
     'CALCULATE_ECL_SEGMENT',
     'VIEW_BANK',
     'CREATE_BANK',
     'UPDATE_BANK',
     'VIEW_BANK_ACCOUNT',
     'CREATE_BANK_ACCOUNT',
     'UPDATE_BANK_ACCOUNT',
     'VIEW_BANK_BRANCH',
     'CREATE_BANK_BRANCH',
     'UPDATE_BANK_BRANCH',
     'VIEW_CASH',
     'CREATE_CASH',
     'UPDATE_CASH',
     'CHANGE_CASH_STATUS',
     'VIEW_CHECKBOOK',
     'CREATE_CHECKBOOK',
     'UPDATE_CHECKBOOK',
     'VIEW_BANK_CHECK',
     'CREATE_BANK_CHECK',
     'UPDATE_BANK_CHECK',
     'RECONCILE_BANK_CHECK',
     'VOID_BANK_CHECK',
     'READ_INVOICE',
     'CREATE_INVOICE_FC',
     'UPDATE_INVOICE',
     'READ_AP_INVOICE',
     'READ_AP_PAYMENT',
     'CREATE_AP_PAYMENT',
     'UPDATE_AP_PAYMENT',
     'READ_AP_ADVANCE',
     'CREATE_AP_ADVANCE',
     'READ_AP_NOTE',
     'CREATE_AP_NOTE',
     'READ_AP_REPORT',
     'READ_PURCHASE_ORDER',
     'CREATE_PURCHASE_ORDER',
     'UPDATE_PURCHASE_ORDER',
     'READ_GOODS_RECEIPT',
     'CREATE_GOODS_RECEIPT',
     'SEARCH_VOUCHER',
     'READ_SALES_INVOICE',
     'CREATE_SALES_INVOICE',
     'UPDATE_SALES_INVOICE',
     'READ_AR_PAYMENT',
     'CREATE_AR_PAYMENT',
     'READ_AR_ADVANCE',
     'CREATE_AR_ADVANCE',
     'READ_AR_NOTE',
     'CREATE_AR_NOTE',
     'READ_DIAN_RESOLUTION',
     'READ_DIAN_REPORT',
     'READ_DIAN',
     'SUBMIT_DIAN',
     'VIEW_TAX_REPORT'
   )
 ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================================================
-- 5. Permisos del rol AUXILIAR_CONTABLE
-- ============================================================================

-- AUXILIAR_CONTABLE: captura basica en AP/AR y consulta
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r CROSS JOIN permissions p
 WHERE r.name = 'AUXILIAR_CONTABLE' AND r.deleted_at IS NULL
   AND p.deleted_at IS NULL
   AND p.code IN (
     'VIEW_THIRD_PARTIES',
     'VIEW_THIRD_PARTY',
     'CREATE_THIRD_PARTY',
     'VIEW_COMMERCIAL_DATA',
     'VIEW_CHART_OF_ACCOUNT',
     'VIEW_ACCOUNTING_ACCOUNT',
     'READ_INVOICE',
     'CREATE_INVOICE_FC',
     'READ_AP_INVOICE',
     'READ_AP_PAYMENT',
     'CREATE_AP_PAYMENT',
     'READ_SALES_INVOICE',
     'CREATE_SALES_INVOICE',
     'READ_AR_PAYMENT',
     'CREATE_AR_PAYMENT',
     'VIEW_BANK',
     'VIEW_BANK_ACCOUNT',
     'VIEW_CASH'
   )
 ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================================================
-- 6. Permisos del rol AUDITOR (solo lectura global)
-- ============================================================================
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r CROSS JOIN permissions p
 WHERE r.name = 'AUDITOR' AND r.deleted_at IS NULL
   AND p.deleted_at IS NULL
   AND (p.type = 'READ' OR p.code IN ('SEARCH_VOUCHER', 'EXPORT_THIRD_PARTY'))
 ON CONFLICT (role_id, permission_id) DO NOTHING;
