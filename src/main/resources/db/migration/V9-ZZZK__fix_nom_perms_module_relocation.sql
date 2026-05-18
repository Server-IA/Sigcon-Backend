-- =====================================================================
-- QA Bloque BI (2026-05-17): dos correcciones del modelo de permisos
-- de Nomina + arreglo cosmetico de PA.
--
-- (A) Reubicar permisos NOM mal asociados al modulo Parametrizacion (id=1)
--     cuando funcionalmente pertenecen al modulo Nomina (id=11).
--
-- (B) Corregir required_permission_code de 5 menus que referencian codes
--     que NO existen en la tabla permissions. Estos menus jamas se mostraban
--     a usuarios non-admin/non-platform porque el filtro de MenuService
--     (Bloque AX, 2026-05-17) compara contra el set de perms del usuario.
--
-- Reporte QA:
-- - Permisos "Ver/Crear/Editar/Eliminar Empleado" (glosario), "Ver/Crear/
--   Editar/Eliminar Conceptos de Nomina" (glosario) y "Ver/Calcular
--   Prestaciones Sociales" aparecen agrupados bajo "Parametrizacion" en el
--   modal de permisos del rol, cuando deberian estar bajo "Nomina".
-- - Tras asignar permisos NOM al rol, los menus "Recibos de nomina",
--   "Reporte PILA" y "Resumen contable por periodo" NO aparecen en el
--   sidebar (aunque el usuario tiene los perms correctos).
--
-- Causa raiz (B): los menus de NOM tienen required_permission_code que
-- referencian codes inexistentes:
--   - menu "Recibos de nomina"     -> exige NOM.RECIBOS.VER     (no existe; el real es NOM.LIQUIDACION.VER)
--   - menu "Reporte PILA"          -> exige NOM.RECIBOS.VER     (no existe; el real es NOM.PILA.GENERAR)
--   - menu "Resumen contable..."   -> exige NOM.RECIBOS.VER     (no existe; el real es NOM.LIQUIDACION.VER)
--   - menu "Navegacion"            -> exige PAR.NAVEGACION.VER  (no existe; el real es PAR.NAVEGACION.EDITAR)
--   - menu "Notificaciones por rol"-> exige PAR.NOTIFICACIONES_ROL.VER  (no existe; el real es PAR.NOTIFICACIONES.CONFIGURAR_ROL)
--
-- Idempotente: ambos UPDATEs filtran por la condicion negada -> re-correr no afecta.
-- =====================================================================

-- ========== (A) Reubicar permisos NOM al modulo correcto ==========
DO $$
DECLARE
    v_nom_module_id BIGINT;
    v_updated INT;
BEGIN
    -- Resolver id del modulo Nomina (defensivo por si cambia de id en seeds futuros)
    SELECT id INTO v_nom_module_id
      FROM modules
     WHERE LOWER(name) IN ('nomina','nómina','nom')
        OR LOWER(url) IN ('nomina','nómina','nom')
     ORDER BY id ASC
     LIMIT 1;

    IF v_nom_module_id IS NULL THEN
        RAISE NOTICE 'V9-ZZZK (A) skip: modulo Nomina no existe. No se reubican permisos.';
    ELSE
        UPDATE permissions
           SET module_id = v_nom_module_id,
               updated_at = NOW()
         WHERE code IN (
                'NOM.EMPLEADOS.VER','NOM.EMPLEADOS.CREAR','NOM.EMPLEADOS.EDITAR','NOM.EMPLEADOS.ELIMINAR',
                'NOM.CONCEPTOS.VER','NOM.CONCEPTOS.CREAR','NOM.CONCEPTOS.EDITAR','NOM.CONCEPTOS.ELIMINAR',
                'NOM.PRESTACIONES.VER','NOM.PRESTACIONES.CALCULAR'
               )
           AND module_id <> v_nom_module_id
           AND deleted_at IS NULL;

        GET DIAGNOSTICS v_updated = ROW_COUNT;
        RAISE NOTICE 'V9-ZZZK (A) ok: % perm(s) NOM reubicados al modulo Nomina (id=%).',
                     v_updated, v_nom_module_id;
    END IF;
END $$;

-- ========== (B) Corregir required_permission_code de 5 menus ==========
DO $$
DECLARE
    v_total INT := 0;
    v_n INT;
BEGIN
    -- Recibos de nomina -> NOM.LIQUIDACION.VER
    UPDATE menus
       SET required_permission_code = 'NOM.LIQUIDACION.VER',
           updated_at = NOW()
     WHERE component = 'NOMINA_RECIBOS'
       AND required_permission_code IS DISTINCT FROM 'NOM.LIQUIDACION.VER'
       AND deleted_at IS NULL;
    GET DIAGNOSTICS v_n = ROW_COUNT; v_total := v_total + v_n;

    -- Reporte PILA -> NOM.PILA.GENERAR
    UPDATE menus
       SET required_permission_code = 'NOM.PILA.GENERAR',
           updated_at = NOW()
     WHERE component = 'NOMINA_PILA'
       AND required_permission_code IS DISTINCT FROM 'NOM.PILA.GENERAR'
       AND deleted_at IS NULL;
    GET DIAGNOSTICS v_n = ROW_COUNT; v_total := v_total + v_n;

    -- Resumen contable -> NOM.LIQUIDACION.VER (vista derivada del periodo liquidado)
    UPDATE menus
       SET required_permission_code = 'NOM.LIQUIDACION.VER',
           updated_at = NOW()
     WHERE component = 'NOMINA_RESUMEN'
       AND required_permission_code IS DISTINCT FROM 'NOM.LIQUIDACION.VER'
       AND deleted_at IS NULL;
    GET DIAGNOSTICS v_n = ROW_COUNT; v_total := v_total + v_n;

    -- Navegacion (PA) -> PAR.NAVEGACION.EDITAR (es la edicion del orden; ver = editar)
    UPDATE menus
       SET required_permission_code = 'PAR.NAVEGACION.EDITAR',
           updated_at = NOW()
     WHERE component = 'NAVEGACION'
       AND required_permission_code IS DISTINCT FROM 'PAR.NAVEGACION.EDITAR'
       AND deleted_at IS NULL;
    GET DIAGNOSTICS v_n = ROW_COUNT; v_total := v_total + v_n;

    -- Notificaciones por rol -> PAR.NOTIFICACIONES.CONFIGURAR_ROL
    UPDATE menus
       SET required_permission_code = 'PAR.NOTIFICACIONES.CONFIGURAR_ROL',
           updated_at = NOW()
     WHERE component = 'NOTIFICACIONES_ROL'
       AND required_permission_code IS DISTINCT FROM 'PAR.NOTIFICACIONES.CONFIGURAR_ROL'
       AND deleted_at IS NULL;
    GET DIAGNOSTICS v_n = ROW_COUNT; v_total := v_total + v_n;

    RAISE NOTICE 'V9-ZZZK (B) ok: % menu(s) con required_permission_code corregido.', v_total;
END $$;
