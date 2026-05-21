-- QA Bloque BO (2026-05-18): mismo patron del Bloque BN-A (NOM menus) pero
-- ahora para los menus NAVEGACION y NOTIFICACIONES_ROL del modulo
-- Parametrizacion.
--
-- Bug raiz: V9-Zzzzf sembraba los 2 menus con codes huerfanos:
--   NAVEGACION         -> 'PAR.NAVEGACION.VER'         (no existe)
--   NOTIFICACIONES_ROL -> 'PAR.NOTIFICACIONES_ROL.VER' (no existe)
--
-- Los codes reales sembrados por V9-ZZW (catalogo glosario v2):
--   PAR.NAVEGACION.EDITAR             (id 8293)
--   PAR.NOTIFICACIONES.CONFIGURAR_ROL (configurar por rol)
--
-- MenuService.getModulesMenu filtra menus cuyo required_permission_code no
-- esta en el set efectivo del usuario. Con codes huerfanos, NINGUN usuario
-- (incluyendo ADMIN_EMPRESA que tiene los perms reales) recibia los menus
-- -> ambos quedaban invisibles en el dropdown del avatar.
--
-- Esta migracion corrige los 2 menus, e incluye un cleanup defensivo para
-- otros menus que pudieran haber heredado codes huerfanos similares.
-- Idempotente: si los menus ya estan corregidos, no hace cambios.

DO $$
DECLARE
    rows_updated INTEGER := 0;
    tmp INTEGER;
BEGIN
    -- NAVEGACION -> PAR.NAVEGACION.EDITAR
    IF EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.NAVEGACION.EDITAR' AND deleted_at IS NULL) THEN
        UPDATE menus
           SET required_permission_code = 'PAR.NAVEGACION.EDITAR',
               updated_at = NOW()
         WHERE component = 'NAVEGACION'
           AND required_permission_code = 'PAR.NAVEGACION.VER'
           AND deleted_at IS NULL;
        GET DIAGNOSTICS tmp = ROW_COUNT;
        rows_updated := rows_updated + tmp;
    ELSE
        RAISE NOTICE 'V9-Zzzzj: permiso PAR.NAVEGACION.EDITAR no existe aun, se omite UPDATE de NAVEGACION.';
    END IF;

    -- NOTIFICACIONES_ROL -> PAR.NOTIFICACIONES.CONFIGURAR_ROL
    IF EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.NOTIFICACIONES.CONFIGURAR_ROL' AND deleted_at IS NULL) THEN
        UPDATE menus
           SET required_permission_code = 'PAR.NOTIFICACIONES.CONFIGURAR_ROL',
               updated_at = NOW()
         WHERE component = 'NOTIFICACIONES_ROL'
           AND required_permission_code = 'PAR.NOTIFICACIONES_ROL.VER'
           AND deleted_at IS NULL;
        GET DIAGNOSTICS tmp = ROW_COUNT;
        rows_updated := rows_updated + tmp;
    ELSE
        RAISE NOTICE 'V9-Zzzzj: permiso PAR.NOTIFICACIONES.CONFIGURAR_ROL no existe aun, se omite UPDATE de NOTIFICACIONES_ROL.';
    END IF;

    IF rows_updated > 0 THEN
        RAISE NOTICE 'V9-Zzzzj: % menu(s) PA corregido(s).', rows_updated;
    ELSE
        RAISE NOTICE 'V9-Zzzzj: ningun menu requirio correccion (ya estaban OK o aun no existian).';
    END IF;
END $$;

-- Cleanup defensivo: cualquier menu que quedo con un required_permission_code
-- apuntando a un permiso inexistente se marca como publico (NULL). Asi
-- evitamos que un code huerfano oculte el menu permanentemente. Solo afecta
-- menus cuyo code NO matchea NINGUN permiso activo.
UPDATE menus m
   SET required_permission_code = NULL,
       updated_at = NOW()
 WHERE m.deleted_at IS NULL
   AND m.required_permission_code IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM permissions p
        WHERE p.code = m.required_permission_code
          AND p.deleted_at IS NULL
   );
