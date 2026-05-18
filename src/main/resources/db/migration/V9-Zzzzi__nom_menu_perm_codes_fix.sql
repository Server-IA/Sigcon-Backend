-- QA Bloque BN (2026-05-18): corregir required_permission_code huerfano en los
-- 3 menus de Nomina (Liquidacion / PILA / Resumen contable).
--
-- Bug raiz:
--   - V9-G (seed inicial NOM v2) y V9-7 (menus integracion+nomina) sembraban
--     los menus NOMINA_RECIBOS/NOMINA_PILA/NOMINA_RESUMEN con
--     required_permission_code='NOM.RECIBOS.VER'.
--   - Pero el set real de permisos de NOM (sembrado por V9-Zzzzb__rbac_complete
--     _roles + V9-G) usa codigos 'NOM.LIQUIDACION.*' y 'NOM.PILA.GENERAR'.
--     'NOM.RECIBOS.VER' nunca existio como permiso.
--   - MenuService.getModulesMenu filtra por required_permission_code: si el
--     usuario NO tiene un perm con ese code, el menu se OCULTA. Como el perm
--     es huerfano, ningun usuario non-admin lo recibe -> 3 menus NOM quedan
--     invisibles a CONTADOR/AUDITOR/usuarios custom con TODOS los perms NOM.
--
-- Mapeo correcto:
--   NOMINA_RECIBOS  (Liquidacion de nomina)  -> NOM.LIQUIDACION.VER
--   NOMINA_PILA     (Reporte PILA)            -> NOM.PILA.GENERAR
--   NOMINA_RESUMEN  (Resumen contable)        -> NOM.LIQUIDACION.VER
--
-- Idempotencia: solo actualizamos cuando el valor actual es el huerfano y
-- existe el permiso destino. Si la BD ya se corrigio, los UPDATE no afectan
-- ninguna fila.

DO $$
DECLARE
    rows_updated INTEGER := 0;
    tmp INTEGER;
BEGIN
    -- NOMINA_RECIBOS -> NOM.LIQUIDACION.VER
    IF EXISTS (SELECT 1 FROM permissions WHERE code = 'NOM.LIQUIDACION.VER' AND deleted_at IS NULL) THEN
        UPDATE menus
           SET required_permission_code = 'NOM.LIQUIDACION.VER',
               updated_at = NOW()
         WHERE component = 'NOMINA_RECIBOS'
           AND required_permission_code = 'NOM.RECIBOS.VER'
           AND deleted_at IS NULL;
        GET DIAGNOSTICS tmp = ROW_COUNT;
        rows_updated := rows_updated + tmp;

        -- NOMINA_RESUMEN -> NOM.LIQUIDACION.VER (mismo permiso)
        UPDATE menus
           SET required_permission_code = 'NOM.LIQUIDACION.VER',
               updated_at = NOW()
         WHERE component = 'NOMINA_RESUMEN'
           AND required_permission_code = 'NOM.RECIBOS.VER'
           AND deleted_at IS NULL;
        GET DIAGNOSTICS tmp = ROW_COUNT;
        rows_updated := rows_updated + tmp;
    ELSE
        RAISE NOTICE 'V9-Zzzzi: permiso NOM.LIQUIDACION.VER no existe aun, se omite UPDATE de NOMINA_RECIBOS/NOMINA_RESUMEN.';
    END IF;

    -- NOMINA_PILA -> NOM.PILA.GENERAR
    IF EXISTS (SELECT 1 FROM permissions WHERE code = 'NOM.PILA.GENERAR' AND deleted_at IS NULL) THEN
        UPDATE menus
           SET required_permission_code = 'NOM.PILA.GENERAR',
               updated_at = NOW()
         WHERE component = 'NOMINA_PILA'
           AND required_permission_code = 'NOM.RECIBOS.VER'
           AND deleted_at IS NULL;
        GET DIAGNOSTICS tmp = ROW_COUNT;
        rows_updated := rows_updated + tmp;
    ELSE
        RAISE NOTICE 'V9-Zzzzi: permiso NOM.PILA.GENERAR no existe aun, se omite UPDATE de NOMINA_PILA.';
    END IF;

    IF rows_updated > 0 THEN
        RAISE NOTICE 'V9-Zzzzi: % menu(s) NOM corregido(s).', rows_updated;
    ELSE
        RAISE NOTICE 'V9-Zzzzi: ningun menu NOM requirio correccion (ya estaban OK o aun no existian).';
    END IF;
END $$;

-- Cleanup defensivo: si quedo algun menu de NOM con el code huerfano
-- 'NOM.RECIBOS.VER' apuntando a algo no contemplado arriba, lo marcamos como
-- publico (NULL) en lugar de dejarlo invisible permanentemente. Solo afecta
-- menus que sigan con el code huerfano y no se hayan resuelto antes.
UPDATE menus
   SET required_permission_code = NULL,
       updated_at = NOW()
 WHERE required_permission_code = 'NOM.RECIBOS.VER'
   AND deleted_at IS NULL;
