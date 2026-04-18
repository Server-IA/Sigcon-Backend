-- V9-7: Registra modulos y menus para Integracion AAEF y Nomina (Fase 5).
-- HU-INT-RF-14, HU-INT-RF-15, HU-NOM-01 a 06.

-- ==========================================================================
-- 1. Modulos nuevos
-- ==========================================================================
INSERT INTO modules (name, description, icon, url, position, status, created_at, updated_at)
SELECT 'Integración AAEF', 'Monitoreo y gestión de lotes AAEF recibidos desde AgroFusion',
       'ri-exchange-line', 'integracion', 9, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM modules WHERE name = 'Integración AAEF' AND deleted_at IS NULL
);

INSERT INTO modules (name, description, icon, url, position, status, created_at, updated_at)
SELECT 'Nómina', 'Gestión de empleados, liquidación de nómina y reporte PILA',
       'ri-team-line', 'nomina', 10, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM modules WHERE name = 'Nómina' AND deleted_at IS NULL
);

-- ==========================================================================
-- 2. Menus Integracion AAEF
-- ==========================================================================
DO $$
DECLARE v_mod_id BIGINT;
BEGIN
    SELECT id INTO v_mod_id FROM modules
      WHERE name = 'Integración AAEF' AND deleted_at IS NULL LIMIT 1;
    IF v_mod_id IS NOT NULL THEN
        -- Lotes AAEF (ruta final: /integracion/lotes, ya que se concatena con module.url='integracion')
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Lotes recibidos', 'ri-file-list-3-line', 'lotes', 10,
               v_mod_id, 'ACTIVE', 'INTEGRACION_LOTES', true, NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM menus WHERE component = 'INTEGRACION_LOTES' AND deleted_at IS NULL
        );
    END IF;
END $$;

-- ==========================================================================
-- 3. Menus Nomina
-- ==========================================================================
DO $$
DECLARE v_mod_id BIGINT;
BEGIN
    SELECT id INTO v_mod_id FROM modules
      WHERE name = 'Nómina' AND deleted_at IS NULL LIMIT 1;
    IF v_mod_id IS NOT NULL THEN
        -- Rutas finales: /nomina/empleados, /nomina/recibos, /nomina/pila
        -- (se concatenan con module.url='nomina')
        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Empleados', 'ri-user-3-line', 'empleados', 10,
               v_mod_id, 'ACTIVE', 'NOMINA_EMPLEADOS', true, NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM menus WHERE component = 'NOMINA_EMPLEADOS' AND deleted_at IS NULL
        );

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Recibos de nómina', 'ri-file-list-line', 'recibos', 20,
               v_mod_id, 'ACTIVE', 'NOMINA_RECIBOS', true, NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM menus WHERE component = 'NOMINA_RECIBOS' AND deleted_at IS NULL
        );

        INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
        SELECT 'Reporte PILA', 'ri-download-cloud-2-line', 'pila', 30,
               v_mod_id, 'ACTIVE', 'NOMINA_PILA', true, NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM menus WHERE component = 'NOMINA_PILA' AND deleted_at IS NULL
        );
    END IF;
END $$;
