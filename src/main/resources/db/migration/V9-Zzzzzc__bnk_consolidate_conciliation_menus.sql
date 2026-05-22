-- =====================================================================
-- F4.4 — Consolidación del sidebar de conciliación bancaria.
-- =====================================================================
-- Toda la conciliación quedó centralizada en la pantalla guiada por cuenta
-- ("Conciliación bancaria", botón junto a cada cuenta bancaria), que la abre
-- con un stepper de 7 pasos + pestaña "Conciliaciones cerradas".
--
-- Esta migración OCULTA del menú lateral SOLO los submódulos 100% absorbidos
-- por esa pantalla (para no dejarlos duplicados/confusos en el sidebar):
--   - MATCHING_WORKSPACE  -> Pasos 4-6 (matching automático + aceptar/rechazar
--                            + emparejamiento manual N:1/1:N/N:M).
--   - SESIONES_FIRMA      -> Paso 7 (cierre en cero + firma + informe PDF +
--                            reapertura/versionado) y la pestaña de cerradas.
--
-- NO se ocultan los módulos de CONFIGURACIÓN ni de REPORTE, que conservan
-- valor standalone fuera del flujo guiado por cuenta:
--   REGLAS_CLASIFICACION, PARAMETROS_MATCHING, CONFIG_FIRMA (config),
--   GMF, PARTIDAS_ANTIGUEDAD, PREPROCESAMIENTO, PARTIDAS_CONCILIATORIAS,
--   SOPORTES_CONCILIACION (integridad/retención de extractos), TRM_HISTORICA,
--   DIFERENCIA_CAMBIO, CRUCE_FE, EXOGENA_DIAN, CONCILIACION_FISCAL.
--
-- Solo cambia la visibilidad en el sidebar (visible=false). La RUTA sigue
-- resolviendo (routes.jsx genera ruta para todo menú), así que un enlace
-- directo previo no se rompe. Idempotente: se puede re-ejecutar sin efecto.
-- Reversible: UPDATE ... SET visible = true ... para restaurar.
-- =====================================================================

UPDATE menus
   SET visible = false,
       updated_at = NOW()
 WHERE component IN ('MATCHING_WORKSPACE', 'SESIONES_FIRMA')
   AND deleted_at IS NULL
   AND visible = true;
