-- QA Bloque AU+ HU-AP-12 E10 + HU-AP-03 E1 (2026-05-07)
-- =============================================================================
-- Fix de 2 cabos sueltos detectados al validar contra prod:
--
-- 1) Los permisos VIEW/CREATE/DELETE_INVOICE_ATTACHMENT habian sido creados
--    inicialmente en V9-J apuntando a module_id=1 (Parametrizacion). El modal
--    "Editar Rol" del frontend filtra los permisos por modulo, asi que NO se
--    veian al editar permisos del modulo "Cuentas por Pagar". Los movemos
--    al modulo correcto. UPDATE_INVOICE_ATTACHMENT ya estaba en CxP porque
--    se creo en V9-ZZZW con module_id resuelto dinamicamente.
--
-- 2) HU-AP-03 E1: el listener AP_INVOICE_SETTLED publica notificacion via
--    NotificationService, pero esa notificacion NO se persiste si el evento
--    no esta en el catalogo. V9-ZZZW solo agrego las suscripciones, faltaba
--    insertar el evento mismo.
-- =============================================================================

DO $$
DECLARE
    v_module_ap BIGINT;
BEGIN
    SELECT id INTO v_module_ap FROM modules
        WHERE name = 'Cuentas por Pagar' AND deleted_at IS NULL LIMIT 1;
    IF v_module_ap IS NULL THEN
        RAISE NOTICE 'V9-ZZZX: modulo Cuentas por Pagar no existe, abort';
        RETURN;
    END IF;

    UPDATE permissions
       SET module_id = v_module_ap, updated_at = NOW()
     WHERE code IN ('VIEW_INVOICE_ATTACHMENT', 'CREATE_INVOICE_ATTACHMENT', 'DELETE_INVOICE_ATTACHMENT')
       AND deleted_at IS NULL
       AND module_id <> v_module_ap;

    RAISE NOTICE 'V9-ZZZX: permisos INVOICE_ATTACHMENT movidos a modulo Cuentas por Pagar (id=%)', v_module_ap;
END $$;

-- HU-AP-03 E1: insertar evento AP_INVOICE_SETTLED en el catalogo si no existe.
-- Sin esto, el listener publica pero NotificationService no encuentra el
-- evento y la notificacion nunca llega a `notifications`.
INSERT INTO notification_event_catalog (event_key, name, description, module, supports_threshold, default_threshold_days, created_at, updated_at)
SELECT 'AP_INVOICE_SETTLED',
       'Factura de compra liquidada',
       'Una factura de compra fue liquidada. CG debe reflejar la deuda saldada con el proveedor (HU-AP-03 E1).',
       'CG',
       false,
       NULL,
       NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM notification_event_catalog WHERE event_key = 'AP_INVOICE_SETTLED'
);
