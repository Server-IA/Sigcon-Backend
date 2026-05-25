-- =====================================================================
-- Reorganizacion definitiva del modulo Bancos y Cajas (solicitud del lider).
-- =====================================================================
-- Requerimiento: toda la conciliacion bancaria se opera desde el boton
-- "Conciliacion" que esta dentro de Cuentas Bancarias. Los apartados
-- independientes de conciliacion / matching / firma / moneda extranjera / DIAN
-- son innecesarios en la navegacion del modulo y deben retirarse del sidebar.
--
-- Migraciones previas (F4.4 / F4.5 / F4.7) ya ocultaron la mayoria, pero
-- DEJARON VISIBLES a proposito estas 6 (como "config global + reportes DIAN").
-- El nuevo requerimiento las retira tambien:
--
--   Grupo 1 (se eliminan de la navegacion; backend queda LATENTE, no se borra):
--     - REGLAS_CLASIFICACION   (Reglas de Clasificacion)
--     - CONFIG_FIRMA           (Configuracion de Firma)
--     - TRM_HISTORICA          (TRM / moneda extranjera)
--     - EXOGENA_DIAN           (Exogena DIAN)
--     - CONCILIACION_FISCAL    (Conciliacion Fiscal)
--
--   Grupo 2 (deja de ser menu; la logica vive dentro de Conciliaciones Bancarias):
--     - PARAMETROS_MATCHING    (tolerancias se ajustan dentro de la sesion)
--
-- Tras esta migracion, en "Bancos y Cajas" quedan visibles SOLO los 8 menus
-- de nucleo operativo: Lista de cajas, Chequeras, Cheques, Bancos,
-- Cuentas Bancarias, Proyecciones Flujo, Arqueos de Caja, Movimientos.
--
-- Solo cambia visibilidad (visible=false): NO se borra ningun menu, ninguna
-- tabla, ningun servicio ni ninguna ruta. routes.jsx sigue generando la ruta
-- para todo menu, asi que cualquier enlace directo previo no se rompe y el
-- backend sigue intacto (latente). Idempotente (no reescribe filas ya en false).
-- Reversible: UPDATE menus SET visible = true WHERE component IN (...).
-- Se acota al modulo 'Bancos y Cajas' por seguridad.
-- =====================================================================

UPDATE menus mn
   SET visible = false,
       updated_at = NOW()
  FROM modules m
 WHERE mn.module_id = m.id
   AND m.name = 'Bancos y Cajas'
   AND mn.deleted_at IS NULL
   AND mn.visible = true
   AND mn.component IN (
       'REGLAS_CLASIFICACION',
       'CONFIG_FIRMA',
       'TRM_HISTORICA',
       'EXOGENA_DIAN',
       'CONCILIACION_FISCAL',
       'PARAMETROS_MATCHING'
   );
