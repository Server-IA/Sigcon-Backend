-- =====================================================================
-- F4.5 — Ocultar el menú "Pre-procesamiento" del sidebar.
-- =====================================================================
-- La clasificación del pre-procesamiento (HU-068) ya está integrada en la
-- pantalla guiada por cuenta:
--   - Al importar el extracto (Paso 3) cada línea se clasifica automáticamente.
--   - La corrección manual de clasificación (HU-068 E8/E10) quedó disponible
--     en la misma tabla del Paso 3 (botón "Corregir clasificación").
-- Por eso el menú lateral standalone "Pre-procesamiento" deja de ser necesario.
--
-- Solo cambia visibilidad (visible=false); la ruta sigue resolviendo. Idempotente
-- y reversible (UPDATE ... SET visible = true ...).
-- =====================================================================

UPDATE menus
   SET visible = false,
       updated_at = NOW()
 WHERE component = 'PREPROCESAMIENTO'
   AND deleted_at IS NULL
   AND visible = true;
