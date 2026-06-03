-- PA-RNF-10 (Pendientes PA, 2026-06-03) punto 2: evento de notificacion para
-- alertar al usuario tras el 3er intento fallido de inicio de sesion.
--
-- AuthService.login() publica este evento (publishToUser) cuando attempts==3.
-- Sin esta fila en el catalogo, lookupEvent lanzaria excepcion y la notificacion
-- no se entregaria (el login no se rompe porque la llamada va en try/catch).
--
-- Idempotente: ON CONFLICT (event_key) DO NOTHING.

INSERT INTO notification_event_catalog (event_key, module, name, description, supports_threshold, default_threshold_days, created_at, updated_at)
VALUES
    ('LOGIN_FAILED_ALERT', 'PA', 'Intentos fallidos de inicio de sesion',
        'Notificacion personal de seguridad: se detectaron multiples intentos fallidos de inicio de sesion en la cuenta del usuario.',
        false, NULL, NOW(), NOW())
ON CONFLICT (event_key) DO NOTHING;
