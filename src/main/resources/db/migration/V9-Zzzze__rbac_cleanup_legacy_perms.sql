-- ============================================================================
-- V9-Zzzze : RBAC cleanup integral - crear codes faltantes + reasignar + soft-delete legacy
-- Fecha: 2026-05-17 (Bloque AX)
--
-- Resuelve bugs reportados por QA:
--   #1 Permisos de Parametrizacion no se asignan (legacy mal-categorizados en module_id=1)
--   #4 Centros costos sin botones (frontend usa plural, BD singular)
--   #6 157 permisos (legacy) duplicados en el modal confunden al admin
--
-- Cambios:
--   1. Crea 33 codes nuevos faltantes que LEGACY_TO_NEW del filter espera
--   2. Para cada rol con legacy, garantiza que tenga el equivalente nuevo asignado
--   3. Soft-delete los 157 permisos legacy
--   4. Limpia roles_permissions huerfanos
--
-- El EffectivePermissionsFilter mantiene alias legacy<->nuevo en runtime,
-- asi los @PreAuthorize que piden PERM_VIEW_USER siguen funcionando con el
-- nuevo PAR.USUARIOS.VER (el filter inyecta ambos en el set de authorities).
-- ============================================================================

-- 1. Crear codes nuevos faltantes (33)
DO $$
BEGIN
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'AP.ANTICIPOS.CREAR', 'Crear - Anticipos', 'Crear - Anticipos', 'CREATE', 7, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'AP.ANTICIPOS.CREAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'AP.ANTICIPOS.VER', 'Ver - Anticipos', 'Ver - Anticipos', 'READ', 7, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'AP.ANTICIPOS.VER' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'AP.FACTURAS_COMPRA.ANULAR', 'Anular - Facturas de compra', 'Anular - Facturas de compra', 'UPDATE', 7, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'AP.FACTURAS_COMPRA.ANULAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'AP.RECEPCIONES.EDITAR', 'Editar - Recepciones', 'Editar - Recepciones', 'UPDATE', 7, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'AP.RECEPCIONES.EDITAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'AR.DIAN.GENERAR', 'Generar - DIAN', 'Generar - DIAN', 'READ', 9, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'AR.DIAN.GENERAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'AR.RESOLUCIONES_DIAN.CREAR', 'Crear - Resoluciones DIAN', 'Crear - Resoluciones DIAN', 'CREATE', 9, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'AR.RESOLUCIONES_DIAN.CREAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'AR.RESOLUCIONES_DIAN.EDITAR', 'Editar - Resoluciones DIAN', 'Editar - Resoluciones DIAN', 'UPDATE', 9, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'AR.RESOLUCIONES_DIAN.EDITAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'AR.RESOLUCIONES_DIAN.ELIMINAR', 'Eliminar - Resoluciones DIAN', 'Eliminar - Resoluciones DIAN', 'DELETE', 9, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'AR.RESOLUCIONES_DIAN.ELIMINAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'AR.RESOLUCIONES_DIAN.VER', 'Ver - Resoluciones DIAN', 'Ver - Resoluciones DIAN', 'READ', 9, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'AR.RESOLUCIONES_DIAN.VER' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'BNK.CHEQUES.EDITAR', 'Editar - Cheques', 'Editar - Cheques', 'UPDATE', 5, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'BNK.CHEQUES.EDITAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'BNK.CHEQUES.ELIMINAR', 'Eliminar - Cheques', 'Eliminar - Cheques', 'DELETE', 5, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'BNK.CHEQUES.ELIMINAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'CFG.CUENTAS.CREAR', 'Crear - Cuentas contables', 'Crear - Cuentas contables', 'CREATE', 2, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.CUENTAS.CREAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'CFG.CUENTAS.EDITAR', 'Editar - Cuentas contables', 'Editar - Cuentas contables', 'UPDATE', 2, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.CUENTAS.EDITAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'CFG.CUENTAS.ELIMINAR', 'Eliminar - Cuentas contables', 'Eliminar - Cuentas contables', 'DELETE', 2, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.CUENTAS.ELIMINAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'CFG.CUENTAS.VER', 'Ver - Cuentas contables', 'Ver - Cuentas contables', 'READ', 2, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.CUENTAS.VER' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'CFG.TASA_CAMBIO.EDITAR', 'Editar - Tasa de cambio', 'Editar - Tasa de cambio', 'UPDATE', 2, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.TASA_CAMBIO.EDITAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'CFG.TASA_CAMBIO.ELIMINAR', 'Eliminar - Tasa de cambio', 'Eliminar - Tasa de cambio', 'DELETE', 2, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.TASA_CAMBIO.ELIMINAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'CFG.TASA_CAMBIO.VER', 'Ver - Tasa de cambio', 'Ver - Tasa de cambio', 'READ', 2, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.TASA_CAMBIO.VER' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'PAR.MENUS.CREAR', 'Crear - Menus', 'Crear - Menus', 'CREATE', 1, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.MENUS.CREAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'PAR.MENUS.EDITAR', 'Editar - Menus', 'Editar - Menus', 'UPDATE', 1, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.MENUS.EDITAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'PAR.MENUS.ELIMINAR', 'Eliminar - Menus', 'Eliminar - Menus', 'DELETE', 1, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.MENUS.ELIMINAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'PAR.MODULOS.CREAR', 'Crear - Modulos', 'Crear - Modulos', 'CREATE', 1, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.MODULOS.CREAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'PAR.MODULOS.EDITAR', 'Editar - Modulos', 'Editar - Modulos', 'UPDATE', 1, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.MODULOS.EDITAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'PAR.MODULOS.ELIMINAR', 'Eliminar - Modulos', 'Eliminar - Modulos', 'DELETE', 1, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.MODULOS.ELIMINAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'PAR.PERMISOS.CREAR', 'Crear - Permisos', 'Crear - Permisos', 'CREATE', 1, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.PERMISOS.CREAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'PAR.PERMISOS.EDITAR', 'Editar - Permisos', 'Editar - Permisos', 'UPDATE', 1, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.PERMISOS.EDITAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'PAR.PERMISOS.ELIMINAR', 'Eliminar - Permisos', 'Eliminar - Permisos', 'DELETE', 1, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.PERMISOS.ELIMINAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'PAR.PERMISOS.VER', 'Ver - Permisos', 'Ver - Permisos', 'READ', 1, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.PERMISOS.VER' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'PAR.REPORTES_TIPOS.CREAR', 'Crear - Tipos de reporte', 'Crear - Tipos de reporte', 'CREATE', 1, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.REPORTES_TIPOS.CREAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'PAR.REPORTES_TIPOS.EDITAR', 'Editar - Tipos de reporte', 'Editar - Tipos de reporte', 'UPDATE', 1, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.REPORTES_TIPOS.EDITAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'PAR.REPORTES_TIPOS.ELIMINAR', 'Eliminar - Tipos de reporte', 'Eliminar - Tipos de reporte', 'DELETE', 1, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.REPORTES_TIPOS.ELIMINAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'TER.SEGMENTACION.AJUSTAR', 'Ajustar - Segmentacion ECL', 'Ajustar - Segmentacion ECL', 'UPDATE', 4, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'TER.SEGMENTACION.AJUSTAR' AND deleted_at IS NULL);
  INSERT INTO permissions (code, name, description, type, module_id, created_at, updated_at)
  SELECT 'TER.SEGMENTACION.VER', 'Ver - Segmentacion ECL', 'Ver - Segmentacion ECL', 'READ', 4, NOW(), NOW()
   WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'TER.SEGMENTACION.VER' AND deleted_at IS NULL);
END $$;

-- 2. Para cada rol con un legacy, asegurar que tenga el equivalente nuevo
DO $$
BEGIN
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'TER.SEGMENTACION.AJUSTAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 438
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'TER.SEGMENTACION.AJUSTAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'TER.SEGMENTACION.AJUSTAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'TER.RIESGO.AJUSTAR_MANUAL' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 438
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'TER.RIESGO.AJUSTAR_MANUAL' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'TER.RIESGO.AJUSTAR_MANUAL' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AP.OC.APROBAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 439
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AP.OC.APROBAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AP.OC.APROBAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.REGLAS_TRIBUTARIAS.ASIGNAR_CUENTA' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 440
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.REGLAS_TRIBUTARIAS.ASIGNAR_CUENTA' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.REGLAS_TRIBUTARIAS.ASIGNAR_CUENTA' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'TER.TERCEROS.IMPORTAR_MASIVO' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 442
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'TER.TERCEROS.IMPORTAR_MASIVO' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'TER.TERCEROS.IMPORTAR_MASIVO' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'TER.SEGMENTACION.AJUSTAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 443
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'TER.SEGMENTACION.AJUSTAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'TER.SEGMENTACION.AJUSTAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'BNK.CAJAS.CAMBIAR_ESTADO' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 444
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'BNK.CAJAS.CAMBIAR_ESTADO' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'BNK.CAJAS.CAMBIAR_ESTADO' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.CUENTAS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 445
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.CUENTAS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.CUENTAS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AP.ANTICIPOS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 446
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AP.ANTICIPOS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AP.ANTICIPOS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AP.NOTAS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 447
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AP.NOTAS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AP.NOTAS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AP.PAGOS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 448
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AP.PAGOS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AP.PAGOS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AR.ANTICIPOS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 449
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AR.ANTICIPOS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AR.ANTICIPOS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AR.NOTAS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 450
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AR.NOTAS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AR.NOTAS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AR.COBROS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 451
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AR.COBROS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AR.COBROS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'ACT.ACTIVOS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 452
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'ACT.ACTIVOS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'ACT.ACTIVOS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'BNK.CAJAS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 453
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'BNK.CAJAS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'BNK.CAJAS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.CUENTAS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 454
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.CUENTAS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.CUENTAS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'TER.DATOS_COMERCIALES.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 455
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'TER.DATOS_COMERCIALES.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'TER.DATOS_COMERCIALES.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.CENTROS_COSTO.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 456
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.CENTROS_COSTO.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.CENTROS_COSTO.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.MONEDAS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 458
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.MONEDAS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.MONEDAS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.DEPRECIACION.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 459
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.DEPRECIACION.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.DEPRECIACION.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AR.RESOLUCIONES_DIAN.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 461
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AR.RESOLUCIONES_DIAN.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AR.RESOLUCIONES_DIAN.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AR.DIAN.GENERAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 462
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AR.DIAN.GENERAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AR.DIAN.GENERAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.TASA_CAMBIO.REGISTRAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 463
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.TASA_CAMBIO.REGISTRAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.TASA_CAMBIO.REGISTRAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AP.RECEPCIONES.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 464
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AP.RECEPCIONES.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AP.RECEPCIONES.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CG.COMPROBANTES.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 466
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CG.COMPROBANTES.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CG.COMPROBANTES.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.MENUS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 467
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.MENUS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.MENUS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.MODULOS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 469
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.MODULOS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.MODULOS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.PERMISOS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 472
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.PERMISOS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.PERMISOS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AP.OC.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 473
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AP.OC.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AP.OC.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.REPORTES_PLANTILLAS.GESTIONAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 474
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.REPORTES_PLANTILLAS.GESTIONAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.REPORTES_PLANTILLAS.GESTIONAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.REPORTES_TIPOS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 475
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.REPORTES_TIPOS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.REPORTES_TIPOS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.REGLAS_TRIBUTARIAS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 476
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.REGLAS_TRIBUTARIAS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.REGLAS_TRIBUTARIAS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.ROLES.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 477
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.ROLES.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.ROLES.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AR.FACTURAS_VENTA.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 478
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AR.FACTURAS_VENTA.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AR.FACTURAS_VENTA.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'TER.TERCEROS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 480
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'TER.TERCEROS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'TER.TERCEROS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.USUARIOS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 481
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.USUARIOS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.USUARIOS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.CUENTAS.ELIMINAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 485
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.CUENTAS.ELIMINAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.CUENTAS.ELIMINAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'ACT.ACTIVOS.DAR_DE_BAJA' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 486
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'ACT.ACTIVOS.DAR_DE_BAJA' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'ACT.ACTIVOS.DAR_DE_BAJA' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'BNK.CAJAS.ELIMINAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 487
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'BNK.CAJAS.ELIMINAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'BNK.CAJAS.ELIMINAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.CUENTAS.ELIMINAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 488
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.CUENTAS.ELIMINAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.CUENTAS.ELIMINAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'TER.DATOS_COMERCIALES.ELIMINAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 489
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'TER.DATOS_COMERCIALES.ELIMINAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'TER.DATOS_COMERCIALES.ELIMINAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.CENTROS_COSTO.ELIMINAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 490
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.CENTROS_COSTO.ELIMINAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.CENTROS_COSTO.ELIMINAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.MONEDAS.ELIMINAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 492
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.MONEDAS.ELIMINAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.MONEDAS.ELIMINAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.DEPRECIACION.ELIMINAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 493
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.DEPRECIACION.ELIMINAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.DEPRECIACION.ELIMINAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AR.RESOLUCIONES_DIAN.ELIMINAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 495
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AR.RESOLUCIONES_DIAN.ELIMINAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AR.RESOLUCIONES_DIAN.ELIMINAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.TASA_CAMBIO.ELIMINAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 496
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.TASA_CAMBIO.ELIMINAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.TASA_CAMBIO.ELIMINAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CG.COMPROBANTES.ANULAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 498
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CG.COMPROBANTES.ANULAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CG.COMPROBANTES.ANULAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.MENUS.ELIMINAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 499
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.MENUS.ELIMINAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.MENUS.ELIMINAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.MODULOS.ELIMINAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 501
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.MODULOS.ELIMINAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.MODULOS.ELIMINAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.PERMISOS.ELIMINAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 504
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.PERMISOS.ELIMINAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.PERMISOS.ELIMINAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.REPORTES_PLANTILLAS.GESTIONAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 505
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.REPORTES_PLANTILLAS.GESTIONAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.REPORTES_PLANTILLAS.GESTIONAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.REPORTES_TIPOS.ELIMINAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 506
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.REPORTES_TIPOS.ELIMINAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.REPORTES_TIPOS.ELIMINAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.ROLES.ELIMINAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 507
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.ROLES.ELIMINAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.ROLES.ELIMINAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.REGLAS_TRIBUTARIAS.ELIMINAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 508
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.REGLAS_TRIBUTARIAS.ELIMINAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.REGLAS_TRIBUTARIAS.ELIMINAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'TER.TERCEROS.DAR_DE_BAJA' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 509
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'TER.TERCEROS.DAR_DE_BAJA' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'TER.TERCEROS.DAR_DE_BAJA' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.USUARIOS.DESACTIVAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 510
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.USUARIOS.DESACTIVAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.USUARIOS.DESACTIVAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'TER.TERCEROS.EXPORTAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 515
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'TER.TERCEROS.EXPORTAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'TER.TERCEROS.EXPORTAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CG.COMPROBANTES.APROBAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 518
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CG.COMPROBANTES.APROBAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CG.COMPROBANTES.APROBAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CG.COMPROBANTES.CONTABILIZAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 518
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CG.COMPROBANTES.CONTABILIZAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CG.COMPROBANTES.CONTABILIZAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AP.OC.RECHAZAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 520
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AP.OC.RECHAZAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AP.OC.RECHAZAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CG.COMPROBANTES.REVERSAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 521
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CG.COMPROBANTES.REVERSAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CG.COMPROBANTES.REVERSAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'TER.TERCEROS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 523
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'TER.TERCEROS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'TER.TERCEROS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AR.DIAN.GENERAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 525
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AR.DIAN.GENERAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AR.DIAN.GENERAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.CUENTAS.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 526
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.CUENTAS.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.CUENTAS.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'ACT.ACTIVOS.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 528
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'ACT.ACTIVOS.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'ACT.ACTIVOS.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'BNK.CAJAS.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 529
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'BNK.CAJAS.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'BNK.CAJAS.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.CUENTAS.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 530
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.CUENTAS.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.CUENTAS.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'TER.DATOS_COMERCIALES.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 531
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'TER.DATOS_COMERCIALES.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'TER.DATOS_COMERCIALES.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.CENTROS_COSTO.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 532
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.CENTROS_COSTO.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.CENTROS_COSTO.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.MONEDAS.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 534
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.MONEDAS.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.MONEDAS.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.DEPRECIACION.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 535
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.DEPRECIACION.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.DEPRECIACION.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AR.RESOLUCIONES_DIAN.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 537
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AR.RESOLUCIONES_DIAN.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AR.RESOLUCIONES_DIAN.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.TASA_CAMBIO.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 538
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.TASA_CAMBIO.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.TASA_CAMBIO.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CG.COMPROBANTES.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 539
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CG.COMPROBANTES.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CG.COMPROBANTES.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.MENUS.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 540
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.MENUS.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.MENUS.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.MODULOS.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 541
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.MODULOS.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.MODULOS.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.PERMISOS.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 544
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.PERMISOS.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.PERMISOS.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.REPORTES_TIPOS.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 545
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.REPORTES_TIPOS.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.REPORTES_TIPOS.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.ROLES.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 546
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.ROLES.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.ROLES.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.REGLAS_TRIBUTARIAS.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 547
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.REGLAS_TRIBUTARIAS.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.REGLAS_TRIBUTARIAS.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AR.FACTURAS_VENTA.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 548
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AR.FACTURAS_VENTA.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AR.FACTURAS_VENTA.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'TER.TERCEROS.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 549
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'TER.TERCEROS.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'TER.TERCEROS.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.USUARIOS.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 550
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.USUARIOS.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.USUARIOS.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.CUENTAS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 554
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.CUENTAS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.CUENTAS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CG.LIBROS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 554
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CG.LIBROS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CG.LIBROS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AP.FACTURAS_COMPRA.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 555
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AP.FACTURAS_COMPRA.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AP.FACTURAS_COMPRA.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'ACT.ACTIVOS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 557
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'ACT.ACTIVOS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'ACT.ACTIVOS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'BNK.BANCOS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 559
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'BNK.BANCOS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'BNK.BANCOS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'BNK.CUENTAS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 560
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'BNK.CUENTAS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'BNK.CUENTAS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'BNK.SUCURSALES.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 561
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'BNK.SUCURSALES.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'BNK.SUCURSALES.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'BNK.CAJAS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 562
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'BNK.CAJAS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'BNK.CAJAS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.CUENTAS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 563
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.CUENTAS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.CUENTAS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CG.LIBROS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 563
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CG.LIBROS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CG.LIBROS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'BNK.CHEQUES.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 564
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'BNK.CHEQUES.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'BNK.CHEQUES.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'BNK.CHEQUERAS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 565
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'BNK.CHEQUERAS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'BNK.CHEQUERAS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'TER.DATOS_COMERCIALES.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 566
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'TER.DATOS_COMERCIALES.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'TER.DATOS_COMERCIALES.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.CENTROS_COSTO.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 567
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.CENTROS_COSTO.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.CENTROS_COSTO.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.MONEDAS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 570
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.MONEDAS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.MONEDAS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.DEPRECIACION.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 572
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.DEPRECIACION.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.DEPRECIACION.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'AR.RESOLUCIONES_DIAN.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 573
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'AR.RESOLUCIONES_DIAN.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'AR.RESOLUCIONES_DIAN.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.TASA_CAMBIO.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 574
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.TASA_CAMBIO.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.TASA_CAMBIO.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.TASA_CAMBIO.REGISTRAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 574
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.TASA_CAMBIO.REGISTRAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.TASA_CAMBIO.REGISTRAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CG.COMPROBANTES.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 577
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CG.COMPROBANTES.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CG.COMPROBANTES.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.MENUS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 578
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.MENUS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.MENUS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.MODULOS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 580
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.MODULOS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.MODULOS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.PERMISOS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 584
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.PERMISOS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.PERMISOS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.REPORTES_PLANTILLAS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 586
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.REPORTES_PLANTILLAS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.REPORTES_PLANTILLAS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.REPORTES_TIPOS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 587
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.REPORTES_TIPOS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.REPORTES_TIPOS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.ROLES.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 588
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.ROLES.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.ROLES.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'CFG.REGLAS_TRIBUTARIAS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 589
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'CFG.REGLAS_TRIBUTARIAS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'CFG.REGLAS_TRIBUTARIAS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'TER.TERCEROS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 590
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'TER.TERCEROS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'TER.TERCEROS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'PAR.USUARIOS.VER' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 591
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'PAR.USUARIOS.VER' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'PAR.USUARIOS.VER' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'BNK.BANCOS.CREAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 632
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'BNK.BANCOS.CREAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'BNK.BANCOS.CREAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'BNK.BANCOS.EDITAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 633
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'BNK.BANCOS.EDITAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'BNK.BANCOS.EDITAR' AND deleted_at IS NULL LIMIT 1));
  INSERT INTO roles_permissions (role_id, permission_id)
  SELECT rp.role_id, (SELECT id FROM permissions WHERE code = 'BNK.BANCOS.ELIMINAR' AND deleted_at IS NULL LIMIT 1)
    FROM roles_permissions rp
   WHERE rp.permission_id = 634
     AND EXISTS (SELECT 1 FROM permissions WHERE code = 'BNK.BANCOS.ELIMINAR' AND deleted_at IS NULL)
     AND NOT EXISTS (SELECT 1 FROM roles_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM permissions WHERE code = 'BNK.BANCOS.ELIMINAR' AND deleted_at IS NULL LIMIT 1));
END $$;

-- 3. Soft-delete los 157 permisos legacy
UPDATE permissions SET deleted_at = NOW(), updated_at = NOW()
 WHERE name ILIKE '%legacy%' AND deleted_at IS NULL;

-- 4. Limpiar roles_permissions huerfanos hacia legacy soft-deleted
DELETE FROM roles_permissions
 WHERE permission_id IN (SELECT id FROM permissions WHERE name ILIKE '%legacy%' AND deleted_at IS NOT NULL);
