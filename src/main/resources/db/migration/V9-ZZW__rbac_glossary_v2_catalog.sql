-- V9-ZZW (2026-05-01): Catalogo de permisos v2 alineado al Glosario.
--
-- REEMPLAZO TOTAL (Option A confirmada por el usuario):
--   - 201 permisos atomicos en formato MODULO.SUBMODULO.ACCION
--   - 7 roles: ADMIN_EMPRESA (rename de ADMIN), CONTADOR, AUXILIAR_CONTABLE,
--     TESORERO (nuevo), AUDITOR, OPERADOR_NOMINA (nuevo), PLATFORM_ADMIN (creado
--     como rol formal con sus 16 permisos del glosario)
--
-- Idempotente: re-ejecutable sin perder asignaciones de usuarios.
-- Los users que tenian rol ADMIN siguen funcionando porque preservamos id=4 al renombrar.

BEGIN;

-- 1) Limpiar TODAS las asignaciones rol->permiso (se rehacen segun glosario).
DELETE FROM roles_permissions;

-- 2) Hard-delete permisos legacy (Option A: limpieza total).
DELETE FROM permissions;

-- 3) Renombrar rol ADMIN -> ADMIN_EMPRESA (idempotente).
--    Caso A: primera corrida (existe ADMIN, no existe ADMIN_EMPRESA) -> rename.
--    Caso B: re-corrida (DataInitializer recreo ADMIN tras rename previo) ->
--            migrar users_roles del ADMIN duplicado al ADMIN_EMPRESA preservado
--            y soft-delete del duplicado para no chocar con uk_roles_active.
DO $$
DECLARE
    v_admin_emp_id BIGINT;
    v_admin_legacy_id BIGINT;
BEGIN
    SELECT id INTO v_admin_emp_id    FROM roles WHERE name='ADMIN_EMPRESA' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_admin_legacy_id FROM roles WHERE name='ADMIN'         AND deleted_at IS NULL LIMIT 1;
    IF v_admin_emp_id IS NULL AND v_admin_legacy_id IS NOT NULL THEN
        UPDATE roles SET name='ADMIN_EMPRESA', updated_at=NOW() WHERE id=v_admin_legacy_id;
    ELSIF v_admin_emp_id IS NOT NULL AND v_admin_legacy_id IS NOT NULL THEN
        INSERT INTO users_roles (user_id, role_id)
        SELECT ur.user_id, v_admin_emp_id FROM users_roles ur
         WHERE ur.role_id = v_admin_legacy_id
           AND NOT EXISTS (SELECT 1 FROM users_roles ur2
                             WHERE ur2.user_id = ur.user_id AND ur2.role_id = v_admin_emp_id);
        DELETE FROM users_roles WHERE role_id = v_admin_legacy_id;
        UPDATE roles SET deleted_at=NOW(), updated_at=NOW() WHERE id=v_admin_legacy_id;
    END IF;
END $$;

-- 4) Soft-delete rol USER legacy (no esta en glosario, 0 usuarios asignados).
UPDATE roles SET deleted_at=NOW(), updated_at=NOW()
 WHERE name='USER' AND deleted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM users_roles ur WHERE ur.role_id = roles.id);

-- 5) Crear roles nuevos si no existen (idempotente).
INSERT INTO roles (name, status, created_at, updated_at)
SELECT 'TESORERO', 'ACTIVE', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name='TESORERO' AND deleted_at IS NULL);

INSERT INTO roles (name, status, created_at, updated_at)
SELECT 'OPERADOR_NOMINA', 'ACTIVE', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name='OPERADOR_NOMINA' AND deleted_at IS NULL);

INSERT INTO roles (name, status, created_at, updated_at)
SELECT 'PLATFORM_ADMIN', 'ACTIVE', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name='PLATFORM_ADMIN' AND deleted_at IS NULL);

-- 6) INSERT 201 permisos del glosario.
--    module_id se resuelve por LIKE para tolerar acentos.
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Facturas de venta', 'AR.FACTURAS_VENTA.VER', 'READ', 'Consultar listado y detalle de facturas de venta de la empresa', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Cobrar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Facturas de venta', 'AR.FACTURAS_VENTA.CREAR', 'CREATE', 'Crear factura de venta nueva con líneas, totales y retenciones', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Cobrar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Facturas de venta', 'AR.FACTURAS_VENTA.EDITAR', 'UPDATE', 'Editar campos de una factura de venta antes de su envío DIAN', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Cobrar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Anular - Facturas de venta', 'AR.FACTURAS_VENTA.ANULAR', 'UPDATE', 'Anular una factura de venta ya emitida (genera nota crédito automática)', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Cobrar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Aplicar NC - Facturas de venta', 'AR.FACTURAS_VENTA.APLICAR_NOTA_CREDITO', 'UPDATE', 'Aplicar una nota crédito a una factura existente', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Cobrar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Exportar PDF - Facturas de venta', 'AR.FACTURAS_VENTA.EXPORTAR_PDF', 'UPDATE', 'Generar y descargar PDF de la factura aplicando identidad visual del tenant', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Cobrar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Anticipos de clientes', 'AR.ANTICIPOS.VER', 'READ', 'Consultar anticipos recibidos de clientes', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Cobrar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Anticipos de clientes', 'AR.ANTICIPOS.CREAR', 'CREATE', 'Registrar nuevo anticipo recibido', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Cobrar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Anticipos de clientes', 'AR.ANTICIPOS.EDITAR', 'UPDATE', 'Editar datos de un anticipo no aplicado', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Cobrar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Aplicar a factura - Anticipos de clientes', 'AR.ANTICIPOS.APLICAR_A_FACTURA', 'UPDATE', 'Aplicar anticipo a una factura de venta pendiente', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Cobrar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Notas crédito/débito AR', 'AR.NOTAS.VER', 'READ', 'Consultar notas crédito y débito emitidas a clientes', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Cobrar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Notas crédito/débito AR', 'AR.NOTAS.CREAR', 'CREATE', 'Crear nota crédito o débito asociada a un cliente', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Cobrar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Cobros y pagos AR', 'AR.COBROS.VER', 'READ', 'Consultar cobros recibidos de clientes', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Cobrar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Cobros y pagos AR', 'AR.COBROS.CREAR', 'CREATE', 'Registrar cobro recibido de un cliente', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Cobrar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Reportes y exportaciones AR', 'AR.REPORTES.VER', 'READ', 'Consultar reportes operativos de AR (cartera, vencimientos, etc.)', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Cobrar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Exportar - Reportes y exportaciones AR', 'AR.REPORTES.EXPORTAR', 'UPDATE', 'Exportar reporte AR en PDF/XLSX', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Cobrar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Facturas de compra', 'AP.FACTURAS_COMPRA.VER', 'READ', 'Consultar facturas de proveedores', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Facturas de compra', 'AP.FACTURAS_COMPRA.CREAR', 'CREATE', 'Registrar factura de compra de proveedor', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Facturas de compra', 'AP.FACTURAS_COMPRA.EDITAR', 'UPDATE', 'Editar factura de compra antes de liquidar', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Liquidar - Facturas de compra', 'AP.FACTURAS_COMPRA.LIQUIDAR', 'UPDATE', 'Liquidar factura aplicando retenciones e impuestos', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Carga masiva - Facturas de compra', 'AP.FACTURAS_COMPRA.CARGA_MASIVA', 'UPDATE', 'Importar lote de facturas de compra desde archivo o AAEF', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Órdenes de compra (OC)', 'AP.OC.VER', 'READ', 'Consultar órdenes de compra', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Órdenes de compra (OC)', 'AP.OC.CREAR', 'CREATE', 'Crear orden de compra a proveedor', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Órdenes de compra (OC)', 'AP.OC.EDITAR', 'UPDATE', 'Editar OC en estado borrador', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Aprobar - Órdenes de compra (OC)', 'AP.OC.APROBAR', 'UPDATE', 'Aprobar OC pendiente', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Rechazar - Órdenes de compra (OC)', 'AP.OC.RECHAZAR', 'UPDATE', 'Rechazar OC con motivo obligatorio', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Recepciones de mercancía', 'AP.RECEPCIONES.VER', 'READ', 'Consultar recepciones de mercancía', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Recepciones de mercancía', 'AP.RECEPCIONES.CREAR', 'CREATE', 'Registrar recepción contra una OC', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Devoluciones a proveedor', 'AP.DEVOLUCIONES.VER', 'READ', 'Consultar devoluciones a proveedor', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Devoluciones a proveedor', 'AP.DEVOLUCIONES.CREAR', 'CREATE', 'Registrar devolución de mercancía a proveedor', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Pagos a proveedores', 'AP.PAGOS.VER', 'READ', 'Consultar pagos realizados a proveedores', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Pagos a proveedores', 'AP.PAGOS.CREAR', 'CREATE', 'Registrar pago a proveedor', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Conciliar - Pagos a proveedores', 'AP.PAGOS.CONCILIAR', 'UPDATE', 'Conciliar pago AP con movimiento bancario', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Notas crédito/débito AP', 'AP.NOTAS.VER', 'READ', 'Consultar notas de proveedor', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Notas crédito/débito AP', 'AP.NOTAS.CREAR', 'CREATE', 'Registrar nota crédito/débito de proveedor', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Reportes y exportaciones AP', 'AP.REPORTES.VER', 'READ', 'Consultar reportes operativos de AP', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Exportar - Reportes y exportaciones AP', 'AP.REPORTES.EXPORTAR', 'UPDATE', 'Exportar reporte AP en PDF/XLSX', NOW(), NOW()
  FROM modules m WHERE m.name='Cuentas por Pagar' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Bancos — catálogo', 'BNK.BANCOS.VER', 'READ', 'Consultar catálogo de bancos del sistema', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Bancos — catálogo', 'BNK.BANCOS.CREAR', 'CREATE', 'Agregar banco al catálogo de la empresa', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Bancos — catálogo', 'BNK.BANCOS.EDITAR', 'UPDATE', 'Editar datos de un banco existente', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Eliminar - Bancos — catálogo', 'BNK.BANCOS.ELIMINAR', 'DELETE', 'Eliminar banco no asociado a movimientos', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Sucursales bancarias', 'BNK.SUCURSALES.VER', 'READ', 'Consultar sucursales', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Sucursales bancarias', 'BNK.SUCURSALES.CREAR', 'CREATE', 'Crear sucursal bancaria', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Sucursales bancarias', 'BNK.SUCURSALES.EDITAR', 'UPDATE', 'Editar sucursal', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Eliminar - Sucursales bancarias', 'BNK.SUCURSALES.ELIMINAR', 'DELETE', 'Eliminar sucursal sin cuentas', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Cuentas bancarias', 'BNK.CUENTAS.VER', 'READ', 'Consultar cuentas bancarias de la empresa', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Cuentas bancarias', 'BNK.CUENTAS.CREAR', 'CREATE', 'Abrir cuenta bancaria en el sistema', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Cuentas bancarias', 'BNK.CUENTAS.EDITAR', 'UPDATE', 'Editar cuenta bancaria', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Eliminar - Cuentas bancarias', 'BNK.CUENTAS.ELIMINAR', 'DELETE', 'Cerrar cuenta sin movimientos pendientes', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Cajas de efectivo', 'BNK.CAJAS.VER', 'READ', 'Consultar cajas de efectivo', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Cajas de efectivo', 'BNK.CAJAS.CREAR', 'CREATE', 'Crear caja de efectivo', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Cajas de efectivo', 'BNK.CAJAS.EDITAR', 'UPDATE', 'Editar caja', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Cambiar estado - Cajas de efectivo', 'BNK.CAJAS.CAMBIAR_ESTADO', 'UPDATE', 'Cambiar estado de caja (Abierta/Cerrada/Bloqueada)', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Chequeras', 'BNK.CHEQUERAS.VER', 'READ', 'Consultar chequeras', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Chequeras', 'BNK.CHEQUERAS.CREAR', 'CREATE', 'Registrar chequera nueva', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Chequeras', 'BNK.CHEQUERAS.EDITAR', 'UPDATE', 'Editar rango de chequera', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Eliminar - Chequeras', 'BNK.CHEQUERAS.ELIMINAR', 'DELETE', 'Eliminar chequera sin cheques emitidos', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Cheques', 'BNK.CHEQUES.VER', 'READ', 'Consultar cheques emitidos', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Emitir - Cheques', 'BNK.CHEQUES.EMITIR', 'CREATE', 'Emitir cheque desde una chequera vigente', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Anular - Cheques', 'BNK.CHEQUES.ANULAR', 'UPDATE', 'Anular cheque emitido', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Conciliar - Cheques', 'BNK.CHEQUES.CONCILIAR', 'UPDATE', 'Conciliar cheque con extracto', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Reportar perdido - Cheques', 'BNK.CHEQUES.REPORTAR_PERDIDO', 'UPDATE', 'Marcar cheque como extraviado y notificar', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Movimientos bancarios', 'BNK.MOVIMIENTOS.VER', 'READ', 'Consultar movimientos bancarios', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Movimientos bancarios', 'BNK.MOVIMIENTOS.CREAR', 'CREATE', 'Registrar movimiento bancario manual', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Movimientos bancarios', 'BNK.MOVIMIENTOS.EDITAR', 'UPDATE', 'Editar movimiento no conciliado', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Anular - Movimientos bancarios', 'BNK.MOVIMIENTOS.ANULAR', 'UPDATE', 'Anular movimiento con motivo', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Conciliación bancaria', 'BNK.CONCILIACION.VER', 'READ', 'Consultar conciliaciones bancarias', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Conciliación bancaria', 'BNK.CONCILIACION.CREAR', 'CREATE', 'Iniciar nueva conciliación', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Conciliación bancaria', 'BNK.CONCILIACION.EDITAR', 'UPDATE', 'Editar conciliación en borrador', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Importar extracto - Conciliación bancaria', 'BNK.CONCILIACION.IMPORTAR_EXTRACTO', 'UPDATE', 'Importar archivo de extracto bancario', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Aprobar - Conciliación bancaria', 'BNK.CONCILIACION.APROBAR', 'UPDATE', 'Aprobar conciliación bancaria', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Anular aprobada - Conciliación bancaria', 'BNK.CONCILIACION.ANULAR_APROBADA', 'UPDATE', 'Anular una conciliación previamente aprobada', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Arqueos de caja', 'BNK.ARQUEOS.VER', 'READ', 'Consultar arqueos de caja', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Arqueos de caja', 'BNK.ARQUEOS.CREAR', 'CREATE', 'Registrar arqueo de caja', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Arqueos de caja', 'BNK.ARQUEOS.EDITAR', 'UPDATE', 'Editar arqueo en borrador', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Aprobar - Arqueos de caja', 'BNK.ARQUEOS.APROBAR', 'UPDATE', 'Aprobar arqueo de caja', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Rechazar - Arqueos de caja', 'BNK.ARQUEOS.RECHAZAR', 'UPDATE', 'Rechazar arqueo con motivo', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Anular aprobado - Arqueos de caja', 'BNK.ARQUEOS.ANULAR_APROBADO', 'UPDATE', 'Anular arqueo previamente aprobado', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Proyecciones de flujo de caja', 'BNK.PROYECCIONES.VER', 'READ', 'Consultar proyecciones de flujo', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Proyecciones de flujo de caja', 'BNK.PROYECCIONES.CREAR', 'CREATE', 'Crear escenario de proyección', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Proyecciones de flujo de caja', 'BNK.PROYECCIONES.EDITAR', 'UPDATE', 'Editar proyección', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Eliminar - Proyecciones de flujo de caja', 'BNK.PROYECCIONES.ELIMINAR', 'DELETE', 'Eliminar proyección', NOW(), NOW()
  FROM modules m WHERE m.name='Bancos y Cajas' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Comprobantes contables', 'CG.COMPROBANTES.VER', 'READ', 'Consultar comprobantes contables', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Comprobantes contables', 'CG.COMPROBANTES.CREAR', 'CREATE', 'Crear comprobante contable', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Comprobantes contables', 'CG.COMPROBANTES.EDITAR', 'UPDATE', 'Editar comprobante en borrador', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Aprobar - Comprobantes contables', 'CG.COMPROBANTES.APROBAR', 'UPDATE', 'Aprobar comprobante pendiente', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Anular - Comprobantes contables', 'CG.COMPROBANTES.ANULAR', 'UPDATE', 'Anular comprobante aprobado', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Reversar - Comprobantes contables', 'CG.COMPROBANTES.REVERSAR', 'READ', 'Reversar comprobante (genera espejo con signo opuesto)', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Períodos contables', 'CG.PERIODOS.VER', 'READ', 'Consultar períodos contables', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Abrir - Períodos contables', 'CG.PERIODOS.ABRIR', 'UPDATE', 'Abrir período contable', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Cerrar - Períodos contables', 'CG.PERIODOS.CERRAR', 'UPDATE', 'Cerrar período contable', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Bloquear (LOCKED) - Períodos contables', 'CG.PERIODOS.BLOQUEAR', 'UPDATE', 'Marcar período como LOCKED (irreversible)', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Libro Diario', 'CG.LIBRO_DIARIO.VER', 'READ', 'Consultar libro diario', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Exportar DIAN - Libro Diario', 'CG.LIBRO_DIARIO.EXPORTAR_DIAN', 'UPDATE', 'Exportar libro diario en formato XML DIAN', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Libro Mayor', 'CG.LIBRO_MAYOR.VER', 'READ', 'Consultar libro mayor', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Exportar DIAN - Libro Mayor', 'CG.LIBRO_MAYOR.EXPORTAR_DIAN', 'UPDATE', 'Exportar libro mayor en formato XML DIAN', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Cierres contables', 'CG.CIERRES.VER', 'READ', 'Consultar cierres ejecutados', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ejecutar mensual - Cierres contables', 'CG.CIERRES.EJECUTAR_MENSUAL', 'UPDATE', 'Ejecutar cierre contable mensual', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ejecutar anual - Cierres contables', 'CG.CIERRES.EJECUTAR_ANUAL', 'UPDATE', 'Ejecutar cierre contable anual', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Estados financieros', 'CG.ESTADOS_FINANCIEROS.VER', 'READ', 'Consultar estados financieros', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Exportar Balance General - Estados financieros', 'CG.ESTADOS_FINANCIEROS.EXPORTAR_BG', 'UPDATE', 'Exportar Balance General en PDF/XLSX', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Exportar Estado de Resultados - Estados financieros', 'CG.ESTADOS_FINANCIEROS.EXPORTAR_ER', 'UPDATE', 'Exportar P&L en PDF/XLSX', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Exportar Flujo de Efectivo - Estados financieros', 'CG.ESTADOS_FINANCIEROS.EXPORTAR_FE', 'UPDATE', 'Exportar Flujo de Efectivo', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Reportes CG', 'CG.REPORTES.VER', 'READ', 'Consultar reportes contables', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Exportar reporte de impuestos - Reportes CG', 'CG.REPORTES.EXPORTAR_IMPUESTOS', 'UPDATE', 'Exportar reporte de impuestos del período', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Exportar reporte comparativo - Reportes CG', 'CG.REPORTES.EXPORTAR_COMPARATIVO', 'UPDATE', 'Exportar comparativo de períodos', NOW(), NOW()
  FROM modules m WHERE m.name='Contabilidad General' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Terceros — catálogo', 'TER.TERCEROS.VER', 'READ', 'Consultar catálogo de terceros (clientes y proveedores)', NOW(), NOW()
  FROM modules m WHERE m.name='Terceros' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Terceros — catálogo', 'TER.TERCEROS.CREAR', 'CREATE', 'Registrar tercero nuevo', NOW(), NOW()
  FROM modules m WHERE m.name='Terceros' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Terceros — catálogo', 'TER.TERCEROS.EDITAR', 'UPDATE', 'Editar datos de un tercero', NOW(), NOW()
  FROM modules m WHERE m.name='Terceros' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Dar de baja - Terceros — catálogo', 'TER.TERCEROS.DAR_DE_BAJA', 'UPDATE', 'Dar de baja tercero (soft delete)', NOW(), NOW()
  FROM modules m WHERE m.name='Terceros' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Importar masivo - Terceros — catálogo', 'TER.TERCEROS.IMPORTAR_MASIVO', 'UPDATE', 'Importar terceros desde XLSX/CSV', NOW(), NOW()
  FROM modules m WHERE m.name='Terceros' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Exportar - Terceros — catálogo', 'TER.TERCEROS.EXPORTAR', 'UPDATE', 'Exportar catálogo completo de terceros', NOW(), NOW()
  FROM modules m WHERE m.name='Terceros' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Cuentas bancarias de terceros', 'TER.CUENTAS_BANCARIAS.VER', 'READ', 'Consultar cuentas bancarias de terceros', NOW(), NOW()
  FROM modules m WHERE m.name='Terceros' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Cuentas bancarias de terceros', 'TER.CUENTAS_BANCARIAS.CREAR', 'CREATE', 'Asociar cuenta bancaria a un tercero', NOW(), NOW()
  FROM modules m WHERE m.name='Terceros' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Cuentas bancarias de terceros', 'TER.CUENTAS_BANCARIAS.EDITAR', 'UPDATE', 'Editar cuenta bancaria de tercero', NOW(), NOW()
  FROM modules m WHERE m.name='Terceros' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Datos comerciales de terceros', 'TER.DATOS_COMERCIALES.VER', 'READ', 'Consultar datos comerciales (cupos, condiciones)', NOW(), NOW()
  FROM modules m WHERE m.name='Terceros' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Datos comerciales de terceros', 'TER.DATOS_COMERCIALES.CREAR', 'CREATE', 'Crear datos comerciales', NOW(), NOW()
  FROM modules m WHERE m.name='Terceros' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Datos comerciales de terceros', 'TER.DATOS_COMERCIALES.EDITAR', 'UPDATE', 'Editar datos comerciales', NOW(), NOW()
  FROM modules m WHERE m.name='Terceros' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Eliminar - Datos comerciales de terceros', 'TER.DATOS_COMERCIALES.ELIMINAR', 'DELETE', 'Eliminar datos comerciales no usados', NOW(), NOW()
  FROM modules m WHERE m.name='Terceros' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Clasificación de riesgo (NIIF 9)', 'TER.RIESGO.VER', 'READ', 'Consultar clasificación de riesgo NIIF 9 de terceros', NOW(), NOW()
  FROM modules m WHERE m.name='Terceros' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ajustar manual - Clasificación de riesgo (NIIF 9)', 'TER.RIESGO.AJUSTAR_MANUAL', 'UPDATE', 'Ajustar segmento de riesgo manualmente con justificación', NOW(), NOW()
  FROM modules m WHERE m.name='Terceros' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Activos', 'ACT.ACTIVOS.VER', 'READ', 'Consultar activos fijos', NOW(), NOW()
  FROM modules m WHERE m.name='Activos' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Activos', 'ACT.ACTIVOS.CREAR', 'CREATE', 'Registrar activo fijo nuevo', NOW(), NOW()
  FROM modules m WHERE m.name='Activos' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Activos', 'ACT.ACTIVOS.EDITAR', 'UPDATE', 'Editar datos de activo', NOW(), NOW()
  FROM modules m WHERE m.name='Activos' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Dar de baja - Activos', 'ACT.ACTIVOS.DAR_DE_BAJA', 'UPDATE', 'Dar de baja activo (venta, destrucción, donación)', NOW(), NOW()
  FROM modules m WHERE m.name='Activos' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ejecutar depreciación - Activos', 'ACT.ACTIVOS.EJECUTAR_DEPRECIACION', 'UPDATE', 'Ejecutar depreciación del período', NOW(), NOW()
  FROM modules m WHERE m.name='Activos' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Revaluar - Activos', 'ACT.ACTIVOS.REVALUAR', 'UPDATE', 'Revaluar activo (NIIF)', NOW(), NOW()
  FROM modules m WHERE m.name='Activos' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Exportar reporte - Activos', 'ACT.ACTIVOS.EXPORTAR_REPORTE', 'UPDATE', 'Exportar reporte de activos', NOW(), NOW()
  FROM modules m WHERE m.name='Activos' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Liquidación de nómina', 'NOM.LIQUIDACION.VER', 'READ', 'Consultar liquidaciones', NOW(), NOW()
  FROM modules m WHERE m.name='Nómina' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Liquidación de nómina', 'NOM.LIQUIDACION.CREAR', 'CREATE', 'Crear liquidación de nómina del período', NOW(), NOW()
  FROM modules m WHERE m.name='Nómina' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Liquidación de nómina', 'NOM.LIQUIDACION.EDITAR', 'UPDATE', 'Editar liquidación en borrador', NOW(), NOW()
  FROM modules m WHERE m.name='Nómina' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Aprobar - Liquidación de nómina', 'NOM.LIQUIDACION.APROBAR', 'UPDATE', 'Aprobar liquidación de nómina', NOW(), NOW()
  FROM modules m WHERE m.name='Nómina' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Cerrar - Liquidación de nómina', 'NOM.LIQUIDACION.CERRAR', 'UPDATE', 'Cerrar nómina del período (irreversible)', NOW(), NOW()
  FROM modules m WHERE m.name='Nómina' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Generar archivo PILA - PILA', 'NOM.PILA.GENERAR', 'CREATE', 'Generar archivo PILA del período', NOW(), NOW()
  FROM modules m WHERE m.name='Nómina' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Exportar - Comprobantes de pago', 'NOM.COMPROBANTES.EXPORTAR', 'UPDATE', 'Exportar comprobantes de pago de nómina', NOW(), NOW()
  FROM modules m WHERE m.name='Nómina' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Log de auditoría', 'AU.LOG.VER', 'READ', 'Consultar log de auditoría operativa de la empresa', NOW(), NOW()
  FROM modules m WHERE m.name='Auditoría' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Exportar - Log de auditoría', 'AU.LOG.EXPORTAR', 'UPDATE', 'Exportar log de auditoría', NOW(), NOW()
  FROM modules m WHERE m.name='Auditoría' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Purgar - Log de auditoría', 'AU.LOG.PURGAR', 'DELETE', 'Ejecutar purga de auditoría según política de retención', NOW(), NOW()
  FROM modules m WHERE m.name='Auditoría' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Lotes AAEF', 'INT.LOTES.VER', 'READ', 'Consultar lotes recibidos de AgroFusion', NOW(), NOW()
  FROM modules m WHERE m.name='Integración AAEF' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver detalle - Lotes AAEF', 'INT.LOTES.VER_DETALLE', 'READ', 'Ver detalle de lote (documentos contenidos, errores)', NOW(), NOW()
  FROM modules m WHERE m.name='Integración AAEF' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Reintentar documento fallido - Lotes AAEF', 'INT.LOTES.REINTENTAR_DOCUMENTO', 'UPDATE', 'Reintentar procesamiento de un documento fallido', NOW(), NOW()
  FROM modules m WHERE m.name='Integración AAEF' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Descargar JSON original - Lotes AAEF', 'INT.LOTES.DESCARGAR_JSON', 'READ', 'Descargar payload original recibido de AgroFusion', NOW(), NOW()
  FROM modules m WHERE m.name='Integración AAEF' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Centros de costo', 'CFG.CENTROS_COSTO.VER', 'READ', 'Consultar centros de costo', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Centros de costo', 'CFG.CENTROS_COSTO.CREAR', 'CREATE', 'Crear centro de costo', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Centros de costo', 'CFG.CENTROS_COSTO.EDITAR', 'UPDATE', 'Editar centro de costo', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Eliminar - Centros de costo', 'CFG.CENTROS_COSTO.ELIMINAR', 'DELETE', 'Eliminar centro de costo sin movimientos', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Reglas de depreciación', 'CFG.DEPRECIACION.VER', 'READ', 'Consultar reglas de depreciación', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Reglas de depreciación', 'CFG.DEPRECIACION.CREAR', 'CREATE', 'Crear regla de depreciación', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Reglas de depreciación', 'CFG.DEPRECIACION.EDITAR', 'UPDATE', 'Editar regla', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Eliminar - Reglas de depreciación', 'CFG.DEPRECIACION.ELIMINAR', 'DELETE', 'Eliminar regla no usada', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Monedas', 'CFG.MONEDAS.VER', 'READ', 'Consultar monedas configuradas', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Monedas', 'CFG.MONEDAS.CREAR', 'CREATE', 'Agregar moneda al catálogo', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Monedas', 'CFG.MONEDAS.EDITAR', 'UPDATE', 'Editar moneda', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Eliminar - Monedas', 'CFG.MONEDAS.ELIMINAR', 'DELETE', 'Eliminar moneda sin transacciones', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Registrar - Tasa de cambio', 'CFG.TASA_CAMBIO.REGISTRAR', 'CREATE', 'Registrar TRM del día por moneda', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Reglas tributarias', 'CFG.REGLAS_TRIBUTARIAS.VER', 'READ', 'Consultar reglas tributarias (retenciones, IVA)', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Reglas tributarias', 'CFG.REGLAS_TRIBUTARIAS.CREAR', 'CREATE', 'Crear regla tributaria', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Reglas tributarias', 'CFG.REGLAS_TRIBUTARIAS.EDITAR', 'UPDATE', 'Editar regla', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Eliminar - Reglas tributarias', 'CFG.REGLAS_TRIBUTARIAS.ELIMINAR', 'DELETE', 'Eliminar regla no usada', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Asignar cuenta contable - Reglas tributarias', 'CFG.REGLAS_TRIBUTARIAS.ASIGNAR_CUENTA', 'UPDATE', 'Asignar cuenta del PUC a una regla tributaria', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Formas de pago', 'CFG.FORMAS_PAGO.VER', 'READ', 'Consultar formas de pago configuradas', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Plazos de pago', 'CFG.PLAZOS_PAGO.VER', 'READ', 'Consultar plazos de pago configurados', NOW(), NOW()
  FROM modules m WHERE m.name='Listas Contables' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Roles', 'PAR.ROLES.VER', 'READ', 'Consultar roles de la empresa', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Roles', 'PAR.ROLES.CREAR', 'CREATE', 'Crear rol personalizado con permisos atómicos (HU-PA-RF2-03)', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Roles', 'PAR.ROLES.EDITAR', 'UPDATE', 'Editar permisos de un rol existente (HU-PA-RF2-04)', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Eliminar - Roles', 'PAR.ROLES.ELIMINAR', 'DELETE', 'Eliminar rol sin usuarios asignados (HU-PA-RF2-05)', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Usuarios', 'PAR.USUARIOS.VER', 'READ', 'Consultar usuarios de la empresa (HU-PA-RF2-06)', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Usuarios', 'PAR.USUARIOS.CREAR', 'CREATE', 'Crear usuario y asignarle uno o varios roles (HU-PA-RF2-07)', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Usuarios', 'PAR.USUARIOS.EDITAR', 'UPDATE', 'Editar datos y roles de un usuario (HU-PA-RF2-08)', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Desactivar - Usuarios', 'PAR.USUARIOS.DESACTIVAR', 'UPDATE', 'Desactivar / reactivar usuario', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Permisos temporales', 'PAR.PERMISOS_TEMPORALES.VER', 'READ', 'Consultar historial de permisos temporales (HU-PA-RF2-15)', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Asignar - Permisos temporales', 'PAR.PERMISOS_TEMPORALES.ASIGNAR', 'UPDATE', 'Asignar permiso temporal a un usuario (HU-PA-RF2-12)', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Revocar - Permisos temporales', 'PAR.PERMISOS_TEMPORALES.REVOCAR', 'UPDATE', 'Revocar manualmente permiso temporal activo (HU-PA-RF2-13)', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Identidad visual', 'PAR.IDENTIDAD_VISUAL.VER', 'READ', 'Consultar configuración de identidad visual', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Identidad visual', 'PAR.IDENTIDAD_VISUAL.EDITAR', 'UPDATE', 'Configurar colores, logo, favicon y nombre comercial (HU-PA-BRAND-01)', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Navegación', 'PAR.NAVEGACION.EDITAR', 'UPDATE', 'Configurar orden de módulos en sidebar (HU-PA-NAV-01)', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Configurar por rol - Notificaciones', 'PAR.NOTIFICACIONES.CONFIGURAR_ROL', 'UPDATE', 'Configurar eventos de notificación al crear/editar rol (HU-PA-RF2-17)', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Menús', 'PAR.MENUS.VER', 'READ', 'Consultar configuración de menús del sistema', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Módulos', 'PAR.MODULOS.VER', 'READ', 'Consultar módulos del sistema', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Parámetros del sistema', 'PAR.PARAMETROS.VER', 'READ', 'Consultar parámetros de configuración', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Parámetros del sistema', 'PAR.PARAMETROS.EDITAR', 'UPDATE', 'Editar parámetros configurables', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Tipos de reporte', 'PAR.REPORTES_TIPOS.VER', 'READ', 'Consultar tipos de reporte disponibles', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Gestionar - Tipos de reporte', 'PAR.REPORTES_TIPOS.GESTIONAR', 'UPDATE', 'Crear, editar o eliminar tipos de reporte', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Plantillas de reporte', 'PAR.REPORTES_PLANTILLAS.VER', 'READ', 'Consultar plantillas de reporte de la empresa', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Gestionar - Plantillas de reporte', 'PAR.REPORTES_PLANTILLAS.GESTIONAR', 'UPDATE', 'Crear nueva versión, editar o eliminar plantilla custom', NOW(), NOW()
  FROM modules m WHERE m.name='Parametrización' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Empresas', 'PLAT.EMPRESAS.VER', 'READ', 'Listar y consultar empresas registradas (HU-PA-PLAT-02)', NOW(), NOW()
  FROM modules m WHERE m.name='Plataforma' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Empresas', 'PLAT.EMPRESAS.CREAR', 'CREATE', 'Crear empresa nueva con aprovisionamiento atómico (HU-PA-PLAT-01)', NOW(), NOW()
  FROM modules m WHERE m.name='Plataforma' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Empresas', 'PLAT.EMPRESAS.EDITAR', 'UPDATE', 'Editar datos legales de empresa', NOW(), NOW()
  FROM modules m WHERE m.name='Plataforma' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Cambiar estado - Empresas', 'PLAT.EMPRESAS.CAMBIAR_ESTADO', 'UPDATE', 'Activar / desactivar empresa (HU-PA-PLAT-03)', NOW(), NOW()
  FROM modules m WHERE m.name='Plataforma' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Usuarios cross-tenant', 'PLAT.USUARIOS.VER', 'READ', 'Consultar usuarios cross-tenant (vista estructural) (HU-PA-PLAT-04)', NOW(), NOW()
  FROM modules m WHERE m.name='Plataforma' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Integración AAEF', 'PLAT.AAEF.VER', 'READ', 'Monitorear lotes AAEF cross-tenant (HU-PA-PLAT-05)', NOW(), NOW()
  FROM modules m WHERE m.name='Plataforma' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Reintentar lote - Integración AAEF', 'PLAT.AAEF.REINTENTAR_LOTE', 'UPDATE', 'Reintentar confirmación de lote a AgroFusion', NOW(), NOW()
  FROM modules m WHERE m.name='Plataforma' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - API Key AgroFusion', 'PLAT.API_KEY.VER', 'READ', 'Consultar historial de rotaciones de API key', NOW(), NOW()
  FROM modules m WHERE m.name='Plataforma' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Rotar - API Key AgroFusion', 'PLAT.API_KEY.ROTAR', 'UPDATE', 'Rotar API key con justificación y período de gracia (HU-PA-PLAT-06)', NOW(), NOW()
  FROM modules m WHERE m.name='Plataforma' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Dashboard de plataforma', 'PLAT.DASHBOARD.VER', 'READ', 'Ver dashboard de salud técnica y uso (HU-PA-PLAT-07)', NOW(), NOW()
  FROM modules m WHERE m.name='Plataforma' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Audit log de plataforma', 'PLAT.AUDIT_LOG.VER', 'READ', 'Consultar log de auditoría de plataforma (HU-PA-PLAT-09)', NOW(), NOW()
  FROM modules m WHERE m.name='Plataforma' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Exportar - Audit log de plataforma', 'PLAT.AUDIT_LOG.EXPORTAR', 'UPDATE', 'Exportar log de plataforma', NOW(), NOW()
  FROM modules m WHERE m.name='Plataforma' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Ver - Usuarios de plataforma', 'PLAT.USUARIOS_PLATAFORMA.VER', 'READ', 'Consultar PLATFORM_ADMINs (HU-PA-PLAT-08)', NOW(), NOW()
  FROM modules m WHERE m.name='Plataforma' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Crear - Usuarios de plataforma', 'PLAT.USUARIOS_PLATAFORMA.CREAR', 'CREATE', 'Crear PLATFORM_ADMIN secundario', NOW(), NOW()
  FROM modules m WHERE m.name='Plataforma' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Editar - Usuarios de plataforma', 'PLAT.USUARIOS_PLATAFORMA.EDITAR', 'UPDATE', 'Editar datos de PLATFORM_ADMIN', NOW(), NOW()
  FROM modules m WHERE m.name='Plataforma' AND m.deleted_at IS NULL;
INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)
SELECT m.id, 'Desactivar - Usuarios de plataforma', 'PLAT.USUARIOS_PLATAFORMA.DESACTIVAR', 'UPDATE', 'Desactivar PLATFORM_ADMIN', NOW(), NOW()
  FROM modules m WHERE m.name='Plataforma' AND m.deleted_at IS NULL;

-- 7) Asignaciones rol -> permisos segun matriz del glosario.

-- ADMIN_EMPRESA: 185 permisos
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r, permissions p
 WHERE r.name='ADMIN_EMPRESA' AND r.deleted_at IS NULL
   AND p.deleted_at IS NULL
   AND p.code IN (
     'AR.FACTURAS_VENTA.VER',
     'AR.FACTURAS_VENTA.CREAR',
     'AR.FACTURAS_VENTA.EDITAR',
     'AR.FACTURAS_VENTA.ANULAR',
     'AR.FACTURAS_VENTA.APLICAR_NOTA_CREDITO',
     'AR.FACTURAS_VENTA.EXPORTAR_PDF',
     'AR.ANTICIPOS.VER',
     'AR.ANTICIPOS.CREAR',
     'AR.ANTICIPOS.EDITAR',
     'AR.ANTICIPOS.APLICAR_A_FACTURA',
     'AR.NOTAS.VER',
     'AR.NOTAS.CREAR',
     'AR.COBROS.VER',
     'AR.COBROS.CREAR',
     'AR.REPORTES.VER',
     'AR.REPORTES.EXPORTAR',
     'AP.FACTURAS_COMPRA.VER',
     'AP.FACTURAS_COMPRA.CREAR',
     'AP.FACTURAS_COMPRA.EDITAR',
     'AP.FACTURAS_COMPRA.LIQUIDAR',
     'AP.FACTURAS_COMPRA.CARGA_MASIVA',
     'AP.OC.VER',
     'AP.OC.CREAR',
     'AP.OC.EDITAR',
     'AP.OC.APROBAR',
     'AP.OC.RECHAZAR',
     'AP.RECEPCIONES.VER',
     'AP.RECEPCIONES.CREAR',
     'AP.DEVOLUCIONES.VER',
     'AP.DEVOLUCIONES.CREAR',
     'AP.PAGOS.VER',
     'AP.PAGOS.CREAR',
     'AP.PAGOS.CONCILIAR',
     'AP.NOTAS.VER',
     'AP.NOTAS.CREAR',
     'AP.REPORTES.VER',
     'AP.REPORTES.EXPORTAR',
     'BNK.BANCOS.VER',
     'BNK.BANCOS.CREAR',
     'BNK.BANCOS.EDITAR',
     'BNK.BANCOS.ELIMINAR',
     'BNK.SUCURSALES.VER',
     'BNK.SUCURSALES.CREAR',
     'BNK.SUCURSALES.EDITAR',
     'BNK.SUCURSALES.ELIMINAR',
     'BNK.CUENTAS.VER',
     'BNK.CUENTAS.CREAR',
     'BNK.CUENTAS.EDITAR',
     'BNK.CUENTAS.ELIMINAR',
     'BNK.CAJAS.VER',
     'BNK.CAJAS.CREAR',
     'BNK.CAJAS.EDITAR',
     'BNK.CAJAS.CAMBIAR_ESTADO',
     'BNK.CHEQUERAS.VER',
     'BNK.CHEQUERAS.CREAR',
     'BNK.CHEQUERAS.EDITAR',
     'BNK.CHEQUERAS.ELIMINAR',
     'BNK.CHEQUES.VER',
     'BNK.CHEQUES.EMITIR',
     'BNK.CHEQUES.ANULAR',
     'BNK.CHEQUES.CONCILIAR',
     'BNK.CHEQUES.REPORTAR_PERDIDO',
     'BNK.MOVIMIENTOS.VER',
     'BNK.MOVIMIENTOS.CREAR',
     'BNK.MOVIMIENTOS.EDITAR',
     'BNK.MOVIMIENTOS.ANULAR',
     'BNK.CONCILIACION.VER',
     'BNK.CONCILIACION.CREAR',
     'BNK.CONCILIACION.EDITAR',
     'BNK.CONCILIACION.IMPORTAR_EXTRACTO',
     'BNK.CONCILIACION.APROBAR',
     'BNK.CONCILIACION.ANULAR_APROBADA',
     'BNK.ARQUEOS.VER',
     'BNK.ARQUEOS.CREAR',
     'BNK.ARQUEOS.EDITAR',
     'BNK.ARQUEOS.APROBAR',
     'BNK.ARQUEOS.RECHAZAR',
     'BNK.ARQUEOS.ANULAR_APROBADO',
     'BNK.PROYECCIONES.VER',
     'BNK.PROYECCIONES.CREAR',
     'BNK.PROYECCIONES.EDITAR',
     'BNK.PROYECCIONES.ELIMINAR',
     'CG.COMPROBANTES.VER',
     'CG.COMPROBANTES.CREAR',
     'CG.COMPROBANTES.EDITAR',
     'CG.COMPROBANTES.APROBAR',
     'CG.COMPROBANTES.ANULAR',
     'CG.COMPROBANTES.REVERSAR',
     'CG.PERIODOS.VER',
     'CG.PERIODOS.ABRIR',
     'CG.PERIODOS.CERRAR',
     'CG.PERIODOS.BLOQUEAR',
     'CG.LIBRO_DIARIO.VER',
     'CG.LIBRO_DIARIO.EXPORTAR_DIAN',
     'CG.LIBRO_MAYOR.VER',
     'CG.LIBRO_MAYOR.EXPORTAR_DIAN',
     'CG.CIERRES.VER',
     'CG.CIERRES.EJECUTAR_MENSUAL',
     'CG.CIERRES.EJECUTAR_ANUAL',
     'CG.ESTADOS_FINANCIEROS.VER',
     'CG.ESTADOS_FINANCIEROS.EXPORTAR_BG',
     'CG.ESTADOS_FINANCIEROS.EXPORTAR_ER',
     'CG.ESTADOS_FINANCIEROS.EXPORTAR_FE',
     'CG.REPORTES.VER',
     'CG.REPORTES.EXPORTAR_IMPUESTOS',
     'CG.REPORTES.EXPORTAR_COMPARATIVO',
     'TER.TERCEROS.VER',
     'TER.TERCEROS.CREAR',
     'TER.TERCEROS.EDITAR',
     'TER.TERCEROS.DAR_DE_BAJA',
     'TER.TERCEROS.IMPORTAR_MASIVO',
     'TER.TERCEROS.EXPORTAR',
     'TER.CUENTAS_BANCARIAS.VER',
     'TER.CUENTAS_BANCARIAS.CREAR',
     'TER.CUENTAS_BANCARIAS.EDITAR',
     'TER.DATOS_COMERCIALES.VER',
     'TER.DATOS_COMERCIALES.CREAR',
     'TER.DATOS_COMERCIALES.EDITAR',
     'TER.DATOS_COMERCIALES.ELIMINAR',
     'TER.RIESGO.VER',
     'TER.RIESGO.AJUSTAR_MANUAL',
     'ACT.ACTIVOS.VER',
     'ACT.ACTIVOS.CREAR',
     'ACT.ACTIVOS.EDITAR',
     'ACT.ACTIVOS.DAR_DE_BAJA',
     'ACT.ACTIVOS.EJECUTAR_DEPRECIACION',
     'ACT.ACTIVOS.REVALUAR',
     'ACT.ACTIVOS.EXPORTAR_REPORTE',
     'NOM.LIQUIDACION.VER',
     'NOM.LIQUIDACION.CREAR',
     'NOM.LIQUIDACION.EDITAR',
     'NOM.LIQUIDACION.APROBAR',
     'NOM.LIQUIDACION.CERRAR',
     'NOM.PILA.GENERAR',
     'NOM.COMPROBANTES.EXPORTAR',
     'AU.LOG.VER',
     'AU.LOG.EXPORTAR',
     'AU.LOG.PURGAR',
     'INT.LOTES.VER',
     'INT.LOTES.VER_DETALLE',
     'INT.LOTES.REINTENTAR_DOCUMENTO',
     'INT.LOTES.DESCARGAR_JSON',
     'CFG.CENTROS_COSTO.VER',
     'CFG.CENTROS_COSTO.CREAR',
     'CFG.CENTROS_COSTO.EDITAR',
     'CFG.CENTROS_COSTO.ELIMINAR',
     'CFG.DEPRECIACION.VER',
     'CFG.DEPRECIACION.CREAR',
     'CFG.DEPRECIACION.EDITAR',
     'CFG.DEPRECIACION.ELIMINAR',
     'CFG.MONEDAS.VER',
     'CFG.MONEDAS.CREAR',
     'CFG.MONEDAS.EDITAR',
     'CFG.MONEDAS.ELIMINAR',
     'CFG.TASA_CAMBIO.REGISTRAR',
     'CFG.REGLAS_TRIBUTARIAS.VER',
     'CFG.REGLAS_TRIBUTARIAS.CREAR',
     'CFG.REGLAS_TRIBUTARIAS.EDITAR',
     'CFG.REGLAS_TRIBUTARIAS.ELIMINAR',
     'CFG.REGLAS_TRIBUTARIAS.ASIGNAR_CUENTA',
     'CFG.FORMAS_PAGO.VER',
     'CFG.PLAZOS_PAGO.VER',
     'PAR.ROLES.VER',
     'PAR.ROLES.CREAR',
     'PAR.ROLES.EDITAR',
     'PAR.ROLES.ELIMINAR',
     'PAR.USUARIOS.VER',
     'PAR.USUARIOS.CREAR',
     'PAR.USUARIOS.EDITAR',
     'PAR.USUARIOS.DESACTIVAR',
     'PAR.PERMISOS_TEMPORALES.VER',
     'PAR.PERMISOS_TEMPORALES.ASIGNAR',
     'PAR.PERMISOS_TEMPORALES.REVOCAR',
     'PAR.IDENTIDAD_VISUAL.VER',
     'PAR.IDENTIDAD_VISUAL.EDITAR',
     'PAR.NAVEGACION.EDITAR',
     'PAR.NOTIFICACIONES.CONFIGURAR_ROL',
     'PAR.MENUS.VER',
     'PAR.MODULOS.VER',
     'PAR.PARAMETROS.VER',
     'PAR.PARAMETROS.EDITAR',
     'PAR.REPORTES_TIPOS.VER',
     'PAR.REPORTES_TIPOS.GESTIONAR',
     'PAR.REPORTES_PLANTILLAS.VER',
     'PAR.REPORTES_PLANTILLAS.GESTIONAR'
   )
 ON CONFLICT DO NOTHING;

-- CONTADOR: 135 permisos
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r, permissions p
 WHERE r.name='CONTADOR' AND r.deleted_at IS NULL
   AND p.deleted_at IS NULL
   AND p.code IN (
     'AR.FACTURAS_VENTA.VER',
     'AR.FACTURAS_VENTA.CREAR',
     'AR.FACTURAS_VENTA.EDITAR',
     'AR.FACTURAS_VENTA.ANULAR',
     'AR.FACTURAS_VENTA.APLICAR_NOTA_CREDITO',
     'AR.FACTURAS_VENTA.EXPORTAR_PDF',
     'AR.ANTICIPOS.VER',
     'AR.ANTICIPOS.CREAR',
     'AR.ANTICIPOS.EDITAR',
     'AR.ANTICIPOS.APLICAR_A_FACTURA',
     'AR.NOTAS.VER',
     'AR.NOTAS.CREAR',
     'AR.COBROS.VER',
     'AR.COBROS.CREAR',
     'AR.REPORTES.VER',
     'AR.REPORTES.EXPORTAR',
     'AP.FACTURAS_COMPRA.VER',
     'AP.FACTURAS_COMPRA.CREAR',
     'AP.FACTURAS_COMPRA.EDITAR',
     'AP.FACTURAS_COMPRA.LIQUIDAR',
     'AP.FACTURAS_COMPRA.CARGA_MASIVA',
     'AP.OC.VER',
     'AP.OC.CREAR',
     'AP.OC.EDITAR',
     'AP.OC.APROBAR',
     'AP.OC.RECHAZAR',
     'AP.RECEPCIONES.VER',
     'AP.RECEPCIONES.CREAR',
     'AP.DEVOLUCIONES.VER',
     'AP.DEVOLUCIONES.CREAR',
     'AP.PAGOS.VER',
     'AP.PAGOS.CREAR',
     'AP.PAGOS.CONCILIAR',
     'AP.NOTAS.VER',
     'AP.NOTAS.CREAR',
     'AP.REPORTES.VER',
     'AP.REPORTES.EXPORTAR',
     'BNK.BANCOS.VER',
     'BNK.SUCURSALES.VER',
     'BNK.CUENTAS.VER',
     'BNK.CAJAS.VER',
     'BNK.CHEQUERAS.VER',
     'BNK.CHEQUES.VER',
     'BNK.CHEQUES.CONCILIAR',
     'BNK.MOVIMIENTOS.VER',
     'BNK.MOVIMIENTOS.CREAR',
     'BNK.MOVIMIENTOS.EDITAR',
     'BNK.MOVIMIENTOS.ANULAR',
     'BNK.CONCILIACION.VER',
     'BNK.CONCILIACION.CREAR',
     'BNK.CONCILIACION.EDITAR',
     'BNK.CONCILIACION.IMPORTAR_EXTRACTO',
     'BNK.CONCILIACION.APROBAR',
     'BNK.ARQUEOS.VER',
     'BNK.ARQUEOS.APROBAR',
     'BNK.ARQUEOS.RECHAZAR',
     'BNK.PROYECCIONES.VER',
     'BNK.PROYECCIONES.CREAR',
     'BNK.PROYECCIONES.EDITAR',
     'BNK.PROYECCIONES.ELIMINAR',
     'CG.COMPROBANTES.VER',
     'CG.COMPROBANTES.CREAR',
     'CG.COMPROBANTES.EDITAR',
     'CG.COMPROBANTES.APROBAR',
     'CG.COMPROBANTES.ANULAR',
     'CG.COMPROBANTES.REVERSAR',
     'CG.PERIODOS.VER',
     'CG.PERIODOS.ABRIR',
     'CG.PERIODOS.CERRAR',
     'CG.LIBRO_DIARIO.VER',
     'CG.LIBRO_DIARIO.EXPORTAR_DIAN',
     'CG.LIBRO_MAYOR.VER',
     'CG.LIBRO_MAYOR.EXPORTAR_DIAN',
     'CG.CIERRES.VER',
     'CG.CIERRES.EJECUTAR_MENSUAL',
     'CG.CIERRES.EJECUTAR_ANUAL',
     'CG.ESTADOS_FINANCIEROS.VER',
     'CG.ESTADOS_FINANCIEROS.EXPORTAR_BG',
     'CG.ESTADOS_FINANCIEROS.EXPORTAR_ER',
     'CG.ESTADOS_FINANCIEROS.EXPORTAR_FE',
     'CG.REPORTES.VER',
     'CG.REPORTES.EXPORTAR_IMPUESTOS',
     'CG.REPORTES.EXPORTAR_COMPARATIVO',
     'TER.TERCEROS.VER',
     'TER.TERCEROS.CREAR',
     'TER.TERCEROS.EDITAR',
     'TER.TERCEROS.DAR_DE_BAJA',
     'TER.TERCEROS.IMPORTAR_MASIVO',
     'TER.TERCEROS.EXPORTAR',
     'TER.CUENTAS_BANCARIAS.VER',
     'TER.CUENTAS_BANCARIAS.CREAR',
     'TER.CUENTAS_BANCARIAS.EDITAR',
     'TER.DATOS_COMERCIALES.VER',
     'TER.DATOS_COMERCIALES.CREAR',
     'TER.DATOS_COMERCIALES.EDITAR',
     'TER.DATOS_COMERCIALES.ELIMINAR',
     'TER.RIESGO.VER',
     'TER.RIESGO.AJUSTAR_MANUAL',
     'ACT.ACTIVOS.VER',
     'ACT.ACTIVOS.CREAR',
     'ACT.ACTIVOS.EDITAR',
     'ACT.ACTIVOS.DAR_DE_BAJA',
     'ACT.ACTIVOS.EJECUTAR_DEPRECIACION',
     'ACT.ACTIVOS.REVALUAR',
     'ACT.ACTIVOS.EXPORTAR_REPORTE',
     'NOM.LIQUIDACION.VER',
     'NOM.LIQUIDACION.APROBAR',
     'NOM.LIQUIDACION.CERRAR',
     'NOM.COMPROBANTES.EXPORTAR',
     'INT.LOTES.VER',
     'INT.LOTES.VER_DETALLE',
     'INT.LOTES.REINTENTAR_DOCUMENTO',
     'INT.LOTES.DESCARGAR_JSON',
     'CFG.CENTROS_COSTO.VER',
     'CFG.CENTROS_COSTO.CREAR',
     'CFG.CENTROS_COSTO.EDITAR',
     'CFG.CENTROS_COSTO.ELIMINAR',
     'CFG.DEPRECIACION.VER',
     'CFG.DEPRECIACION.CREAR',
     'CFG.DEPRECIACION.EDITAR',
     'CFG.DEPRECIACION.ELIMINAR',
     'CFG.MONEDAS.VER',
     'CFG.MONEDAS.CREAR',
     'CFG.MONEDAS.EDITAR',
     'CFG.MONEDAS.ELIMINAR',
     'CFG.TASA_CAMBIO.REGISTRAR',
     'CFG.REGLAS_TRIBUTARIAS.VER',
     'CFG.REGLAS_TRIBUTARIAS.CREAR',
     'CFG.REGLAS_TRIBUTARIAS.EDITAR',
     'CFG.REGLAS_TRIBUTARIAS.ELIMINAR',
     'CFG.REGLAS_TRIBUTARIAS.ASIGNAR_CUENTA',
     'CFG.FORMAS_PAGO.VER',
     'CFG.PLAZOS_PAGO.VER',
     'PAR.REPORTES_TIPOS.VER',
     'PAR.REPORTES_PLANTILLAS.VER'
   )
 ON CONFLICT DO NOTHING;

-- AUXILIAR_CONTABLE: 52 permisos
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r, permissions p
 WHERE r.name='AUXILIAR_CONTABLE' AND r.deleted_at IS NULL
   AND p.deleted_at IS NULL
   AND p.code IN (
     'AR.FACTURAS_VENTA.VER',
     'AR.FACTURAS_VENTA.CREAR',
     'AR.FACTURAS_VENTA.EXPORTAR_PDF',
     'AR.ANTICIPOS.VER',
     'AR.ANTICIPOS.CREAR',
     'AR.ANTICIPOS.APLICAR_A_FACTURA',
     'AR.NOTAS.VER',
     'AR.COBROS.VER',
     'AR.COBROS.CREAR',
     'AR.REPORTES.VER',
     'AP.FACTURAS_COMPRA.VER',
     'AP.FACTURAS_COMPRA.CREAR',
     'AP.OC.VER',
     'AP.OC.CREAR',
     'AP.OC.EDITAR',
     'AP.RECEPCIONES.VER',
     'AP.RECEPCIONES.CREAR',
     'AP.DEVOLUCIONES.VER',
     'AP.DEVOLUCIONES.CREAR',
     'AP.PAGOS.VER',
     'AP.NOTAS.VER',
     'AP.REPORTES.VER',
     'BNK.BANCOS.VER',
     'BNK.SUCURSALES.VER',
     'BNK.CUENTAS.VER',
     'BNK.CAJAS.VER',
     'BNK.CHEQUERAS.VER',
     'BNK.CHEQUES.VER',
     'BNK.MOVIMIENTOS.VER',
     'BNK.CONCILIACION.VER',
     'BNK.ARQUEOS.VER',
     'BNK.PROYECCIONES.VER',
     'CG.COMPROBANTES.VER',
     'CG.PERIODOS.VER',
     'CG.LIBRO_DIARIO.VER',
     'CG.LIBRO_MAYOR.VER',
     'CG.ESTADOS_FINANCIEROS.VER',
     'CG.REPORTES.VER',
     'TER.TERCEROS.VER',
     'TER.TERCEROS.CREAR',
     'TER.TERCEROS.EDITAR',
     'TER.CUENTAS_BANCARIAS.VER',
     'TER.CUENTAS_BANCARIAS.CREAR',
     'TER.CUENTAS_BANCARIAS.EDITAR',
     'TER.DATOS_COMERCIALES.VER',
     'TER.DATOS_COMERCIALES.CREAR',
     'TER.DATOS_COMERCIALES.EDITAR',
     'ACT.ACTIVOS.VER',
     'CFG.CENTROS_COSTO.VER',
     'CFG.MONEDAS.VER',
     'CFG.FORMAS_PAGO.VER',
     'CFG.PLAZOS_PAGO.VER'
   )
 ON CONFLICT DO NOTHING;

-- TESORERO: 66 permisos
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r, permissions p
 WHERE r.name='TESORERO' AND r.deleted_at IS NULL
   AND p.deleted_at IS NULL
   AND p.code IN (
     'AR.FACTURAS_VENTA.VER',
     'AR.FACTURAS_VENTA.EXPORTAR_PDF',
     'AR.ANTICIPOS.VER',
     'AR.NOTAS.VER',
     'AR.COBROS.VER',
     'AR.COBROS.CREAR',
     'AR.REPORTES.VER',
     'AP.FACTURAS_COMPRA.VER',
     'AP.OC.VER',
     'AP.RECEPCIONES.VER',
     'AP.DEVOLUCIONES.VER',
     'AP.PAGOS.VER',
     'AP.PAGOS.CREAR',
     'AP.PAGOS.CONCILIAR',
     'AP.NOTAS.VER',
     'AP.REPORTES.VER',
     'BNK.BANCOS.VER',
     'BNK.BANCOS.CREAR',
     'BNK.BANCOS.EDITAR',
     'BNK.BANCOS.ELIMINAR',
     'BNK.SUCURSALES.VER',
     'BNK.SUCURSALES.CREAR',
     'BNK.SUCURSALES.EDITAR',
     'BNK.SUCURSALES.ELIMINAR',
     'BNK.CUENTAS.VER',
     'BNK.CUENTAS.CREAR',
     'BNK.CUENTAS.EDITAR',
     'BNK.CUENTAS.ELIMINAR',
     'BNK.CAJAS.VER',
     'BNK.CAJAS.CREAR',
     'BNK.CAJAS.EDITAR',
     'BNK.CAJAS.CAMBIAR_ESTADO',
     'BNK.CHEQUERAS.VER',
     'BNK.CHEQUERAS.CREAR',
     'BNK.CHEQUERAS.EDITAR',
     'BNK.CHEQUERAS.ELIMINAR',
     'BNK.CHEQUES.VER',
     'BNK.CHEQUES.EMITIR',
     'BNK.CHEQUES.ANULAR',
     'BNK.CHEQUES.CONCILIAR',
     'BNK.CHEQUES.REPORTAR_PERDIDO',
     'BNK.MOVIMIENTOS.VER',
     'BNK.MOVIMIENTOS.CREAR',
     'BNK.MOVIMIENTOS.EDITAR',
     'BNK.MOVIMIENTOS.ANULAR',
     'BNK.CONCILIACION.VER',
     'BNK.CONCILIACION.CREAR',
     'BNK.CONCILIACION.EDITAR',
     'BNK.CONCILIACION.IMPORTAR_EXTRACTO',
     'BNK.ARQUEOS.VER',
     'BNK.ARQUEOS.CREAR',
     'BNK.ARQUEOS.EDITAR',
     'BNK.PROYECCIONES.VER',
     'BNK.PROYECCIONES.CREAR',
     'BNK.PROYECCIONES.EDITAR',
     'BNK.PROYECCIONES.ELIMINAR',
     'CG.COMPROBANTES.VER',
     'CG.PERIODOS.VER',
     'TER.TERCEROS.VER',
     'TER.CUENTAS_BANCARIAS.VER',
     'TER.DATOS_COMERCIALES.VER',
     'CFG.CENTROS_COSTO.VER',
     'CFG.MONEDAS.VER',
     'CFG.TASA_CAMBIO.REGISTRAR',
     'CFG.FORMAS_PAGO.VER',
     'CFG.PLAZOS_PAGO.VER'
   )
 ON CONFLICT DO NOTHING;

-- AUDITOR: 60 permisos
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r, permissions p
 WHERE r.name='AUDITOR' AND r.deleted_at IS NULL
   AND p.deleted_at IS NULL
   AND p.code IN (
     'AR.FACTURAS_VENTA.VER',
     'AR.FACTURAS_VENTA.EXPORTAR_PDF',
     'AR.ANTICIPOS.VER',
     'AR.NOTAS.VER',
     'AR.COBROS.VER',
     'AR.REPORTES.VER',
     'AR.REPORTES.EXPORTAR',
     'AP.FACTURAS_COMPRA.VER',
     'AP.OC.VER',
     'AP.RECEPCIONES.VER',
     'AP.DEVOLUCIONES.VER',
     'AP.PAGOS.VER',
     'AP.NOTAS.VER',
     'AP.REPORTES.VER',
     'AP.REPORTES.EXPORTAR',
     'BNK.BANCOS.VER',
     'BNK.SUCURSALES.VER',
     'BNK.CUENTAS.VER',
     'BNK.CAJAS.VER',
     'BNK.CHEQUERAS.VER',
     'BNK.CHEQUES.VER',
     'BNK.MOVIMIENTOS.VER',
     'BNK.CONCILIACION.VER',
     'BNK.ARQUEOS.VER',
     'BNK.PROYECCIONES.VER',
     'CG.COMPROBANTES.VER',
     'CG.PERIODOS.VER',
     'CG.LIBRO_DIARIO.VER',
     'CG.LIBRO_DIARIO.EXPORTAR_DIAN',
     'CG.LIBRO_MAYOR.VER',
     'CG.LIBRO_MAYOR.EXPORTAR_DIAN',
     'CG.CIERRES.VER',
     'CG.ESTADOS_FINANCIEROS.VER',
     'CG.ESTADOS_FINANCIEROS.EXPORTAR_BG',
     'CG.ESTADOS_FINANCIEROS.EXPORTAR_ER',
     'CG.ESTADOS_FINANCIEROS.EXPORTAR_FE',
     'CG.REPORTES.VER',
     'CG.REPORTES.EXPORTAR_IMPUESTOS',
     'CG.REPORTES.EXPORTAR_COMPARATIVO',
     'TER.TERCEROS.VER',
     'TER.TERCEROS.EXPORTAR',
     'TER.CUENTAS_BANCARIAS.VER',
     'TER.DATOS_COMERCIALES.VER',
     'TER.RIESGO.VER',
     'ACT.ACTIVOS.VER',
     'ACT.ACTIVOS.EXPORTAR_REPORTE',
     'NOM.LIQUIDACION.VER',
     'NOM.COMPROBANTES.EXPORTAR',
     'AU.LOG.VER',
     'AU.LOG.EXPORTAR',
     'INT.LOTES.VER',
     'INT.LOTES.VER_DETALLE',
     'CFG.CENTROS_COSTO.VER',
     'CFG.DEPRECIACION.VER',
     'CFG.MONEDAS.VER',
     'CFG.REGLAS_TRIBUTARIAS.VER',
     'CFG.FORMAS_PAGO.VER',
     'CFG.PLAZOS_PAGO.VER',
     'PAR.REPORTES_TIPOS.VER',
     'PAR.REPORTES_PLANTILLAS.VER'
   )
 ON CONFLICT DO NOTHING;

-- OPERADOR_NOMINA: 7 permisos
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r, permissions p
 WHERE r.name='OPERADOR_NOMINA' AND r.deleted_at IS NULL
   AND p.deleted_at IS NULL
   AND p.code IN (
     'CG.COMPROBANTES.VER',
     'NOM.LIQUIDACION.VER',
     'NOM.LIQUIDACION.CREAR',
     'NOM.LIQUIDACION.EDITAR',
     'NOM.PILA.GENERAR',
     'NOM.COMPROBANTES.EXPORTAR',
     'CFG.CENTROS_COSTO.VER'
   )
 ON CONFLICT DO NOTHING;

-- PLATFORM_ADMIN: 16 permisos
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r, permissions p
 WHERE r.name='PLATFORM_ADMIN' AND r.deleted_at IS NULL
   AND p.deleted_at IS NULL
   AND p.code IN (
     'PLAT.EMPRESAS.VER',
     'PLAT.EMPRESAS.CREAR',
     'PLAT.EMPRESAS.EDITAR',
     'PLAT.EMPRESAS.CAMBIAR_ESTADO',
     'PLAT.USUARIOS.VER',
     'PLAT.AAEF.VER',
     'PLAT.AAEF.REINTENTAR_LOTE',
     'PLAT.API_KEY.VER',
     'PLAT.API_KEY.ROTAR',
     'PLAT.DASHBOARD.VER',
     'PLAT.AUDIT_LOG.VER',
     'PLAT.AUDIT_LOG.EXPORTAR',
     'PLAT.USUARIOS_PLATAFORMA.VER',
     'PLAT.USUARIOS_PLATAFORMA.CREAR',
     'PLAT.USUARIOS_PLATAFORMA.EDITAR',
     'PLAT.USUARIOS_PLATAFORMA.DESACTIVAR'
   )
 ON CONFLICT DO NOTHING;

-- 8) Asegurar que admin@sigcondemo.test (caso historico sin rol asignado)
--    tenga ADMIN_EMPRESA. Sin esto, ese usuario no podria operar.
INSERT INTO users_roles (user_id, role_id)
SELECT u.id, r.id
  FROM users u, roles r
 WHERE u.email='admin@sigcondemo.test' AND u.deleted_at IS NULL
   AND r.name='ADMIN_EMPRESA' AND r.deleted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM users_roles ur WHERE ur.user_id=u.id AND ur.role_id=r.id);

-- 9) superadmin@gmail.com: dejar SOLO con platform_role + sin rol tenant
--    (PLATFORM_ADMIN tiene rol formal aparte para que herede los 16 permisos del glosario,
--     pero superadmin no necesita asignacion porque platform_role del JWT lo identifica).
--    Sin embargo, si tiene rol ADMIN_EMPRESA legado, lo dejamos para no romper su sesion.

COMMIT;

-- Resumen de la migracion:
-- Total permisos: 201
-- Mapeo TypePermits: {'READ': 62, 'CREATE': 36, 'UPDATE': 91, 'DELETE': 12}
-- Permisos por rol:
--   ADMIN_EMPRESA: 185
--   CONTADOR: 135
--   AUXILIAR_CONTABLE: 52
--   TESORERO: 66
--   AUDITOR: 60
--   OPERADOR_NOMINA: 7
--   PLATFORM_ADMIN: 16