-- V9-Z9: Corrige el path del menu "Sucursales Bancos" para que matchee con
-- la navegacion relativa del frontend (desde /cash-and-banks/banks → navigate
-- a `branches/${row.id}` resuelve a /cash-and-banks/banks/branches/:id).
-- Antes V1-2 crea el menu con path='branches/:id' (sin prefijo banks/)
-- pero el frontend navega a 'banks/branches/...'. Esto genera 403.
--
-- Como V1-2 corre ANTES de V9-Z9 y re-inserta en cada arranque, aqui:
-- 1. Eliminamos cualquier menu duplicado con path='branches/:id'
-- 2. Actualizamos el path del restante a 'banks/branches/:id'
-- Idempotente: funciona en cualquier estado previo (fresh, ya arreglado, mix).

DO $$
DECLARE
    v_existing_fixed BOOLEAN;
BEGIN
    -- Si ya existe el menu con path correcto, eliminar el duplicado con path viejo
    SELECT EXISTS(SELECT 1 FROM menus
                   WHERE component = 'SUCURSALES_BANCARIAS'
                     AND path = 'banks/branches/:id'
                     AND deleted_at IS NULL)
      INTO v_existing_fixed;

    IF v_existing_fixed THEN
        -- Hay menu correcto -> borrar cualquier duplicado con path viejo
        DELETE FROM menus
         WHERE component = 'SUCURSALES_BANCARIAS'
           AND path = 'branches/:id';
    ELSE
        -- Solo existe el menu con path viejo -> actualizarlo
        UPDATE menus
           SET path = 'banks/branches/:id', updated_at = NOW()
         WHERE component = 'SUCURSALES_BANCARIAS'
           AND path = 'branches/:id'
           AND deleted_at IS NULL;
    END IF;
END $$;

-- Bug adicional descubierto en QA (Bloque N+1):
-- SUCURSALES_BANCARIAS tenia parent_id apuntando al menu CATALOGO_BANCOS
-- (path='banks'). El renderMenuRoutesFlat del frontend concatena el path
-- del padre con el del child, generando ruta DUPLICADA:
--   /cash-and-banks/banks/banks/branches/:id (incorrecta)
-- En vez de la esperada:
--   /cash-and-banks/banks/branches/:id
--
-- Como el frontend navega con `/cash-and-banks/banks/branches/8`, la ruta no
-- matchea ninguna Route registrada y cae a CatchAllRoute → 403 (porque
-- /cash-and-banks/banks/ esta en allSystemPaths).
--
-- Fix: setear parent_id = NULL para que el menu sea standalone con path
-- absoluto 'banks/branches/:id'. Sigue oculto del sidebar (visible=false).
UPDATE menus SET parent_id = NULL, updated_at = NOW()
 WHERE component = 'SUCURSALES_BANCARIAS' AND parent_id IS NOT NULL;

-- Bug similar en UPDATE_ASSETS: el frontend navega a `edit/${id}` (relativo
-- al path actual `/assets/assets`) pero el menu BD tiene path='update/:id'.
-- Resultado: 403 al editar un activo. Fix: cambiar path a 'edit/:id'.
-- Mantenemos parent_id porque el path es relativo (no duplica el `assets`).
-- Idempotente: si ya existe el menu correcto con 'edit/:id', borrar el viejo
-- para no chocar con UNIQUE(module_id, path); si solo existe el viejo, actualizarlo.
DO $$
BEGIN
    IF EXISTS(SELECT 1 FROM menus WHERE component='UPDATE_ASSETS' AND path='edit/:id' AND deleted_at IS NULL) THEN
        DELETE FROM menus WHERE component='UPDATE_ASSETS' AND path='update/:id';
    ELSE
        UPDATE menus SET path = 'edit/:id', updated_at = NOW()
         WHERE component = 'UPDATE_ASSETS' AND path = 'update/:id' AND deleted_at IS NULL;
    END IF;
END $$;

SELECT 'V9-Z9 aplicado: sucursales fix + assets edit fix' AS status;
