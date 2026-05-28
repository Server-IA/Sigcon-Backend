-- ============================================================================
-- HU-ACT-08: Revisión anual de vida útil y valor residual (cambio de estimación)
--
-- El backend de revisión anual (NiifAlertsController: GET /annual-review/assets,
-- POST /annual-review, entidad AssetAnnualReview) YA existía, pero no tenía
-- acceso en la interfaz: no había menú ni página → el requerimiento "no se veía
-- por ninguna parte" en el sistema.
--
-- Esta migración agrega el menú "Revisión Anual de Activos" bajo el módulo
-- Activos (component ACT_REVISION_ANUAL, ruta /assets/annual-review), que el
-- frontend ya mapea a la nueva página pages/assets/revision-anual/index.jsx.
--
-- Idempotente: WHERE NOT EXISTS por component.
-- ============================================================================
DO $$
DECLARE
    v_act BIGINT;
BEGIN
    SELECT id INTO v_act FROM modules WHERE name = 'Activos' AND deleted_at IS NULL LIMIT 1;
    IF v_act IS NOT NULL THEN
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, required_permission_code, created_at, updated_at)
        SELECT 'Revisión Anual de Activos', 'ri-calendar-check-line', 'annual-review', 7, v_act, 'ACTIVE', 'ACT_REVISION_ANUAL', true, 'ACT.ACTIVOS.VER', NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'ACT_REVISION_ANUAL' AND deleted_at IS NULL);
    END IF;
END $$;
