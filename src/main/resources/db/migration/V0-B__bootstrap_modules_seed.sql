-- V0-B (2026-05-10) - HOTFIX preventivo CRITICO.
--
-- Contexto:
--   V1-2 (menus) y V1-3 (permissions) hardcodean module_id=1, 2, 3, 4, 5
--   asumiendo que V1-1 inserto los modulos legacy en orden y la sequence
--   modules_id_seq estaba en 1 al arrancar. Si la tabla modules quedo:
--     - vacia (sequence ya avanzada),
--     - con filas pero ids distintos a 1..5,
--     - con filas soft-deleted dejando la sequence en valores feos,
--   los INSERT de V1-2 / V1-3 fallan con FK violation y el backend NO arranca.
--
-- Esta migracion ordena V0- (antes de V1, V2, ..., V10) e inserta los 5
-- modulos legacy con IDs EXPLICITOS si no existen, y luego sincroniza la
-- sequence a max(id)+1. Asi V1-2 / V1-3 siempre encuentran los module_id
-- esperados.
--
-- Comportamiento:
--   - Idempotente: re-ejecutar no duplica ni rompe (ON CONFLICT DO NOTHING).
--   - Preserva datos: NO borra modulos existentes ni cambia ids ya asignados.
--   - Defensivo: si la tabla modules ya esta perfecta, la sequence se ajusta
--     pero no se altera nada mas.
--
-- Modulos que vienen DESPUES de los legacy 5 (Plataforma id=6, Cuentas por
-- Pagar id=7, etc.) se insertan en sus migraciones propias (V10-E, V24, V25,
-- etc.) sin depender de id especifico — por eso no requieren bootstrap.

-- Insert defensivo por cada modulo: si el id YA esta tomado o el url YA existe
-- activo, salta. Asi nunca rompe constraints UNIQUE de id o url.
INSERT INTO modules (id, created_at, deleted_at, description, icon, name, "position", status, updated_at, url)
SELECT 1, NOW(), NULL, 'Gestión de parámetros del sistema', 'bx-cog', 'Parametrización', 1, 'ACTIVE', NOW(), 'parametrizacion'
 WHERE NOT EXISTS (SELECT 1 FROM modules WHERE id = 1)
   AND NOT EXISTS (SELECT 1 FROM modules WHERE url = 'parametrizacion' AND deleted_at IS NULL);

INSERT INTO modules (id, created_at, deleted_at, description, icon, name, "position", status, updated_at, url)
SELECT 2, NOW(), NULL, 'Gestión de listas contables', 'ri-list-ordered-2', 'Listas Contables', 2, 'ACTIVE', NOW(), 'lists-accounting'
 WHERE NOT EXISTS (SELECT 1 FROM modules WHERE id = 2)
   AND NOT EXISTS (SELECT 1 FROM modules WHERE url = 'lists-accounting' AND deleted_at IS NULL);

INSERT INTO modules (id, created_at, deleted_at, description, icon, name, "position", status, updated_at, url)
SELECT 3, NOW(), NULL, 'Gestion de activos del sistema', 'ri-todo-line', 'Activos', 3, 'ACTIVE', NOW(), 'assets'
 WHERE NOT EXISTS (SELECT 1 FROM modules WHERE id = 3)
   AND NOT EXISTS (SELECT 1 FROM modules WHERE url = 'assets' AND deleted_at IS NULL);

INSERT INTO modules (id, created_at, deleted_at, description, icon, name, "position", status, updated_at, url)
SELECT 4, NOW(), NULL, 'Gestion de terceros para el sistema', 'ri-team-line', 'Terceros', 4, 'ACTIVE', NOW(), 'thirds'
 WHERE NOT EXISTS (SELECT 1 FROM modules WHERE id = 4)
   AND NOT EXISTS (SELECT 1 FROM modules WHERE url = 'thirds' AND deleted_at IS NULL);

INSERT INTO modules (id, created_at, deleted_at, description, icon, name, "position", status, updated_at, url)
SELECT 5, NOW(), NULL, 'Gestión de bancos y cajas', 'ri-bank-line', 'Bancos y Cajas', 5, 'ACTIVE', NOW(), 'cash-and-banks'
 WHERE NOT EXISTS (SELECT 1 FROM modules WHERE id = 5)
   AND NOT EXISTS (SELECT 1 FROM modules WHERE url = 'cash-and-banks' AND deleted_at IS NULL);

-- Sincronizar la sequence al maximo id existente + 1, asi futuros INSERTs sin
-- id explicito (V10-E, V24, etc.) obtienen ids correlativos sin colisionar.
SELECT setval('modules_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM modules), 1), true);
