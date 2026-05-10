-- =====================================================================
-- HU-PA-18 E5 (Bloque PA Bug 50, 2026-05-09)
-- =====================================================================
-- Completa el catalogo de eventos de notificacion segun la lista 8.1 v2.
-- Eventos faltantes que se agregan:
--   * CG: PERIOD_CLOSED, PERIOD_BLOCKED, MONTHLY_CLOSE_EXECUTED, ANNUAL_CLOSE_EXECUTED
--   * AR: AR_ADVANCE_UNAPPLIED_5D
--   * BNK: BNK_CHECK_LOST
--   * AU: AUDIT_UNAUTHORIZED_ACTION
--   * PA (USER_EVENT, sistemicos no configurables): USER_DEACTIVATED,
--         USER_ROLE_ADDED, USER_ROLE_REMOVED, USER_VOUCHER_REJECTED,
--         ROLE_PERMISSIONS_CHANGED (HU-PA-20 E2/E3/E4/E5)
--
-- Idempotente: usa event_key UNIQUE.
-- =====================================================================

INSERT INTO notification_event_catalog (event_key, module, name, description, supports_threshold, default_threshold_days, created_at, updated_at)
VALUES
    ('PERIOD_CLOSED', 'CG', 'Periodo cerrado',
        'Notifica cuando un periodo contable es cerrado.',
        false, NULL, NOW(), NOW()),
    ('PERIOD_BLOCKED', 'CG', 'Periodo bloqueado',
        'Notifica cuando un periodo contable es bloqueado permanentemente.',
        false, NULL, NOW(), NOW()),
    ('MONTHLY_CLOSE_EXECUTED', 'CG', 'Cierre mensual ejecutado',
        'Notifica cuando se ejecuta el cierre mensual.',
        false, NULL, NOW(), NOW()),
    ('ANNUAL_CLOSE_EXECUTED', 'CG', 'Cierre anual ejecutado',
        'Notifica cuando se ejecuta el cierre anual.',
        false, NULL, NOW(), NOW()),
    ('AR_ADVANCE_UNAPPLIED_5D', 'AR', 'Anticipo sin aplicar > 5 dias',
        'Notifica cuando un anticipo de cliente lleva mas de 5 dias sin aplicarse a una factura.',
        true, 5, NOW(), NOW()),
    ('BNK_CHECK_LOST', 'BNK', 'Cheque extraviado',
        'Notifica cuando se reporta un cheque como extraviado.',
        false, NULL, NOW(), NOW()),
    ('AUDIT_UNAUTHORIZED_ACTION', 'AU', 'Accion no autorizada detectada',
        'Notifica cuando el motor de auditoria detecta una accion no autorizada.',
        false, NULL, NOW(), NOW()),
    ('USER_DEACTIVATED', 'PA', 'Usuario desactivado',
        'Notificacion personal: la cuenta del usuario fue desactivada por un administrador.',
        false, NULL, NOW(), NOW()),
    ('USER_ROLE_ADDED', 'PA', 'Rol agregado',
        'Notificacion personal: se le agrego un rol nuevo al usuario.',
        false, NULL, NOW(), NOW()),
    ('USER_ROLE_REMOVED', 'PA', 'Rol retirado',
        'Notificacion personal: se le retiro un rol al usuario.',
        false, NULL, NOW(), NOW()),
    ('USER_VOUCHER_REJECTED', 'PA', 'Comprobante rechazado',
        'Notificacion personal al creador cuando su comprobante es rechazado/reversado.',
        false, NULL, NOW(), NOW()),
    ('ROLE_PERMISSIONS_CHANGED', 'PA', 'Permisos del rol modificados',
        'Notificacion personal: los permisos de un rol que tiene asignado fueron modificados.',
        false, NULL, NOW(), NOW())
ON CONFLICT (event_key) DO NOTHING;
