-- ============================================================================
-- V9-ZZZN: Eventos de notificacion para flujo de Ordenes de Compra (HU-AP-17)
-- ----------------------------------------------------------------------------
-- QA-BLOQUE-AY (2026-05-05): el modulo AP carecia de eventos de notificacion
-- para los cambios de estado de Ordenes de Compra. La HU-AP-17 exige avisar al
-- solicitante cuando su OC es aprobada o rechazada, y al aprobador cuando hay
-- OCs pendientes a revisar. Sin estos eventos en el catalogo, las
-- notificaciones quedaban en silencio aunque el codigo del service intentara
-- publicarlas.
--
-- Idempotente: usa ON CONFLICT DO NOTHING sobre la UK natural (event_key).
-- ============================================================================

INSERT INTO notification_event_catalog (event_key, name, description, module, supports_threshold, default_threshold_days, created_at, updated_at)
SELECT v.event_key, v.name, v.description, v.module, v.supports_threshold, v.default_threshold_days, NOW(), NOW()
FROM (VALUES
  ('PO_APPROVED',         'Orden de compra aprobada',             'Notifica al solicitante que su orden de compra fue aprobada por el revisor.', 'AP', false, NULL::INTEGER),
  ('PO_REJECTED',         'Orden de compra rechazada',            'Notifica al solicitante que su orden de compra fue rechazada con motivo.',     'AP', false, NULL),
  ('PO_PENDING_APPROVAL', 'Orden de compra pendiente de aprobar', 'Avisa a los aprobadores que hay una orden de compra esperando decision.',     'AP', false, NULL)
) AS v(event_key, name, description, module, supports_threshold, default_threshold_days)
WHERE NOT EXISTS (SELECT 1 FROM notification_event_catalog c WHERE c.event_key = v.event_key);

-- Suscripcion default: roles ADMIN/ADMIN_EMPRESA/CONTADOR reciben los 3 eventos
-- por defecto en cada empresa. Cada admin de empresa puede ajustarlo desde la
-- pagina de notificaciones por rol (UI Parametrizacion).
-- UNIQUE existente: (role_id, event_key) WHERE deleted_at IS NULL.
-- No incluye company_id, por lo que solo cabe 1 fila por (role,event).
-- Insertamos sin CROSS JOIN companies y con NOT EXISTS para idempotencia.
INSERT INTO role_notification_subscriptions (role_id, company_id, event_key, enabled, threshold_days, created_at, updated_at)
SELECT r.id, NULL, ek, true, NULL, NOW(), NOW()
FROM roles r
CROSS JOIN (VALUES ('PO_APPROVED'), ('PO_REJECTED'), ('PO_PENDING_APPROVAL')) AS evt(ek)
WHERE r.name IN ('ADMIN','ADMIN_EMPRESA','CONTADOR')
  AND r.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM role_notification_subscriptions s
    WHERE s.role_id = r.id AND s.event_key = ek AND s.deleted_at IS NULL
  );
