-- =====================================================================
-- F4.7 — Ocultar del sidebar las 6 herramientas de conciliación por-cuenta.
-- =====================================================================
-- Estas herramientas operan sobre UNA cuenta bancaria, así que se centralizaron
-- dentro del panel de "Conciliación bancaria" (pestaña "Herramientas"), que ya
-- fija la cuenta por la URL. Por eso dejan de necesitar un menú lateral propio:
--   - GMF                      (GMF 4x1000: validación + reporte por cuenta)
--   - PARTIDAS_CONCILIATORIAS  (detectar/generar ajustes por cuenta)
--   - PARTIDAS_ANTIGUEDAD      (antigüedad de partidas por cuenta)
--   - SOPORTES_CONCILIACION    (integridad/retención de extractos por cuenta)
--   - CRUCE_FE                 (cruce con factura electrónica por cuenta)
--   - DIFERENCIA_CAMBIO        (diferencia en cambio NIC 21 por cuenta)
--
-- Permanecen en el sidebar (config global de empresa + reportes fiscales DIAN):
--   REGLAS_CLASIFICACION, PARAMETROS_MATCHING, CONFIG_FIRMA, TRM_HISTORICA,
--   EXOGENA_DIAN, CONCILIACION_FISCAL.
--
-- Solo cambia visibilidad (visible=false); la ruta sigue resolviendo. Idempotente
-- y reversible (UPDATE ... SET visible = true ...).
-- =====================================================================

UPDATE menus
   SET visible = false,
       updated_at = NOW()
 WHERE component IN ('GMF', 'PARTIDAS_CONCILIATORIAS', 'PARTIDAS_ANTIGUEDAD',
                     'SOPORTES_CONCILIACION', 'CRUCE_FE', 'DIFERENCIA_CAMBIO')
   AND deleted_at IS NULL
   AND visible = true;
