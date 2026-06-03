-- RF-34 (Notas Tecnicas CXP, 2026-06-02): permiso granular para reversar pagos
-- a proveedores.
--
-- El sistema usa codigos glosario como `code` (ej. AP.PAGOS.CREAR). El boton
-- "Reversar" del frontend se muestra solo si el usuario tiene el permiso
-- AP.PAGOS.REVERSAR. Sin este permiso, CONTADOR/TESORERO/ADMIN_EMPRESA no veian
-- el boton (el backend ya lo permitia via ROLE_ADMIN_EMPRESA, pero quedaba
-- inconsistente con el FE y bloqueaba a CONTADOR/TESORERO).
--
-- El EffectivePermissionsFilter mapea REVERSE_PAYMENT <-> AP.PAGOS.REVERSAR, asi
-- que el @PreAuthorize("PERM_REVERSE_PAYMENT") del controller tambien pasa para
-- quien tenga este permiso glosario.
--
-- Idempotente: re-ejecutable sin duplicar.

-- 1) Crear el permiso (global, compartido entre empresas) si no existe.
INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
SELECT 'AP.PAGOS.REVERSAR', 'Reversar - Pagos a proveedores',
       'Permite reversar pagos/abonos a proveedores (RF-34)', 'UPDATE', 7, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE code = 'AP.PAGOS.REVERSAR' AND deleted_at IS NULL
);

-- 2) Asignar el permiso a todos los roles que YA pueden crear pagos
--    (ADMIN_EMPRESA, CONTADOR, TESORERO de cada empresa).
INSERT INTO roles_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id,
       (SELECT id FROM permissions WHERE code = 'AP.PAGOS.REVERSAR' AND deleted_at IS NULL LIMIT 1)
FROM roles_permissions rp
JOIN permissions p ON p.id = rp.permission_id
WHERE p.code = 'AP.PAGOS.CREAR'
  AND p.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM roles_permissions rp2
      WHERE rp2.role_id = rp.role_id
        AND rp2.permission_id = (
            SELECT id FROM permissions WHERE code = 'AP.PAGOS.REVERSAR' AND deleted_at IS NULL LIMIT 1)
  );
