-- Sprint 4 - HU-PA-18 a HU-PA-25: notificaciones in-app
-- Compatible con DataInitializer (solo dollar quotes anonimos $$).

-- =============================================================
-- 1) Catalogo de eventos del sistema (read-only desde la UI)
-- =============================================================
CREATE TABLE IF NOT EXISTS notification_event_catalog (
    id BIGSERIAL PRIMARY KEY,
    event_key VARCHAR(80) NOT NULL UNIQUE,
    module VARCHAR(20) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    -- Si el evento es de tipo "vencimiento proximo" (factura, permiso temp), permite umbral en dias.
    supports_threshold BOOLEAN NOT NULL DEFAULT FALSE,
    default_threshold_days INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_notif_evt_module CHECK (module IN ('CG','AR','AP','BNK','NOM','INT','AU','PA','TER','ACT','CFG'))
);
CREATE INDEX IF NOT EXISTS idx_notif_evt_module ON notification_event_catalog(module);

-- Seed inicial: eventos vigentes en SIGCON. Se pueden agregar mas via SQL en futuras migraciones.
INSERT INTO notification_event_catalog(event_key, module, name, description, supports_threshold, default_threshold_days, created_at, updated_at)
SELECT v.event_key, v.module, v.name, v.description, v.supports_threshold, v.default_threshold_days, NOW(), NOW()
  FROM (VALUES
    -- CG
    ('VOUCHER_PENDING_APPROVAL','CG','Comprobante pendiente de aprobacion','Se creo un comprobante en BORRADOR esperando aprobacion',FALSE,NULL),
    ('PERIOD_CLOSE_REMINDER','CG','Cierre de periodo proximo','El periodo contable esta proximo a cerrarse',TRUE,7),
    -- AR
    ('AR_INVOICE_DUE_SOON','AR','Factura de venta proxima a vencer','Una factura de venta vence en N dias',TRUE,7),
    ('AR_INVOICE_OVERDUE','AR','Factura de venta vencida sin pagar','Una factura de venta supero su fecha de vencimiento sin estar pagada',FALSE,NULL),
    -- AP
    ('AP_INVOICE_DUE_SOON','AP','Factura de compra proxima a vencer','Una factura de compra vence en N dias',TRUE,7),
    ('AP_INVOICE_OVERDUE','AP','Factura de compra vencida','Una factura de compra supero su fecha de vencimiento',FALSE,NULL),
    -- BNK
    ('BANK_RECONCILIATION_PENDING','BNK','Conciliacion bancaria pendiente','Hay una conciliacion bancaria en estado BORRADOR sin cerrar',FALSE,NULL),
    -- NOM
    ('PAYROLL_APPROVAL_PENDING','NOM','Liquidacion de nomina pendiente de aprobacion','Una liquidacion de nomina espera aprobacion',FALSE,NULL),
    -- INT
    ('AAEF_BATCH_FAILED','INT','Lote AAEF fallido','Un lote AAEF de AgroFusion fue rechazado o tuvo errores',FALSE,NULL),
    -- AU
    ('AUDIT_RISK_ALERT','AU','Alerta de riesgo de auditoria','Se detecto un evento de severidad HIGH/CRITICAL',FALSE,NULL),
    -- PA (no configurable por rol; siempre se envia, type=USER_EVENT)
    ('TEMP_PERMISSION_ASSIGNED','PA','Le fue asignado un permiso temporal','Un admin le otorgo un permiso temporal',FALSE,NULL),
    ('TEMP_PERMISSION_REVOKED','PA','Su permiso temporal fue revocado','Un admin revoco un permiso temporal antes de su vencimiento',FALSE,NULL),
    ('TEMP_PERMISSION_EXPIRING','PA','Su permiso temporal vence en 24h','El permiso temporal asignado vence en menos de 24 horas',FALSE,NULL),
    ('TEMP_PERMISSION_EXPIRED','PA','Su permiso temporal vencio','Un permiso temporal asignado expiro',FALSE,NULL),
    ('PASSWORD_CHANGED','PA','Su contrasena fue actualizada','La contrasena de su cuenta fue cambiada',FALSE,NULL)
  ) AS v(event_key, module, name, description, supports_threshold, default_threshold_days)
 WHERE NOT EXISTS (SELECT 1 FROM notification_event_catalog c WHERE c.event_key = v.event_key);

-- =============================================================
-- 2) Suscripciones por rol (HU-PA-18)
-- =============================================================
CREATE TABLE IF NOT EXISTS role_notification_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT,
    role_id BIGINT NOT NULL,
    event_key VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    threshold_days INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_role_notif_role FOREIGN KEY (role_id) REFERENCES roles(id),
    CONSTRAINT fk_role_notif_event FOREIGN KEY (event_key) REFERENCES notification_event_catalog(event_key)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_role_notif_role_event_active
  ON role_notification_subscriptions(role_id, event_key)
  WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_role_notif_company ON role_notification_subscriptions(company_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_role_notif_event ON role_notification_subscriptions(event_key) WHERE deleted_at IS NULL AND enabled = TRUE;

-- =============================================================
-- 3) Notificaciones generadas
-- =============================================================
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    module VARCHAR(20) NOT NULL,
    event_key VARCHAR(80) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body TEXT,
    action_url VARCHAR(500),
    source_id BIGINT,
    source_type VARCHAR(80),
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    read_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_notif_type CHECK (type IN ('ROL_EVENT','USER_EVENT','SYSTEM')),
    CONSTRAINT ck_notif_module CHECK (module IN ('CG','AR','AP','BNK','NOM','INT','AU','PA','TER','ACT','CFG')),
    CONSTRAINT ck_notif_severity CHECK (severity IN ('INFO','WARNING','CRITICAL')),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_notif_company FOREIGN KEY (company_id) REFERENCES companies(id)
);
CREATE INDEX IF NOT EXISTS idx_notif_user_unread ON notifications(user_id, created_at DESC) WHERE read_at IS NULL AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_notif_user_created ON notifications(user_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_notif_dedup ON notifications(user_id, event_key, source_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_notif_expires ON notifications(expires_at) WHERE deleted_at IS NULL;

-- =============================================================
-- 4) Permisos
-- =============================================================
INSERT INTO permissions(code, name, description, type, module_id, created_at, updated_at)
SELECT v.code, v.name, v.description, v.ptype, m.id, NOW(), NOW()
  FROM (VALUES
    ('PAR.NOTIFICACIONES.VER','Ver mis notificaciones','HU-PA-21','READ'),
    ('PAR.NOTIFICACIONES.MARCAR','Marcar notificaciones como leidas','HU-PA-22','UPDATE'),
    ('PAR.NOTIFICACIONES.CONFIGURAR_ROL','Configurar eventos de notificacion en rol','HU-PA-18','UPDATE')
  ) AS v(code,name,description,ptype)
  CROSS JOIN (SELECT id FROM modules WHERE name ILIKE 'parametr%' AND deleted_at IS NULL LIMIT 1) m
 WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code AND p.deleted_at IS NULL);

-- VER + MARCAR los recibe TODO usuario autenticado (estos los gestiona el endpoint con isAuthenticated()).
-- CONFIGURAR_ROL solo ADMIN_EMPRESA.
INSERT INTO roles_permissions(role_id, permission_id)
SELECT r.id, p.id
  FROM roles r, permissions p
 WHERE r.name='ADMIN_EMPRESA' AND r.deleted_at IS NULL
   AND p.code='PAR.NOTIFICACIONES.CONFIGURAR_ROL' AND p.deleted_at IS NULL
ON CONFLICT DO NOTHING;
