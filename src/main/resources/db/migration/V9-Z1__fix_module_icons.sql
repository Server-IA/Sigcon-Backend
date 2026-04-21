-- =============================================================================
-- V9-Z1: corrige iconos de modulos que usaban BoxIcons (bx-*) y no Remix Icons.
--
-- El frontend renderiza los iconos de las tarjetas del dashboard con
-- <i className={mod.icon}> usando la libreria Remix Icons (prefijo ri-).
-- El modulo Parametrizacion tenia "bx-cog" que no existe en esa libreria y
-- quedaba como circulo vacio en el dashboard.
-- =============================================================================
UPDATE modules
   SET icon = 'ri-settings-3-line', updated_at = NOW()
 WHERE name = 'Parametrización' AND (icon IS NULL OR icon NOT LIKE 'ri-%')
   AND deleted_at IS NULL;
