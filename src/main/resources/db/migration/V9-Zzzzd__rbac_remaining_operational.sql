-- ============================================================================
-- V9-Zzzzd : Bloque AY+ - Ultimos gaps operativos CONTADOR
-- Fecha: 2026-05-16 (renombrada 2026-05-17, antes V9-ZZZZK)
-- Renombre forzado por bug de orden lexical (ver V9-Zzzza header).
--
-- Tras V9-Zzzzc quedan 21 endpoints donde CONTADOR da 403. De estos, ~14 son
-- operativos legitimos del rol contable:
--   * Crear/editar cuentas contables (plan de cuentas operativo)
--   * Contabilizar (post) asientos
--   * Eliminar asientos en BORRADOR (CG.COMPROBANTES.ELIMINAR)
--   * Registrar movimientos en cuentas bancarias
--   * Conciliar pagos AP/AR
--   * Aplicar anticipos AP
--   * Conciliar status facturas
--   * Toggle/Inactivar chequeras
--
-- Quedan como 403 correcto (ADMIN_EMPRESA only):
--   * Eliminar OC, empleados, conceptos NOM, lineas de recibos (gestion sensible)
--   * run-overdue-scheduler (administrativo)
--   * Lista global de permisos temporales
-- ============================================================================

DO $$
DECLARE
    rec RECORD;
    mapping RECORD;
    role_perm_id BIGINT;
BEGIN
    FOR rec IN
        SELECT r.id AS role_id, r.name AS role_name
          FROM roles r
         WHERE r.deleted_at IS NULL
           AND r.name IN ('CONTADOR', 'AUDITOR', 'AUXILIAR_CONTABLE', 'TESORERO')
    LOOP
        FOR mapping IN
            SELECT * FROM (VALUES
                -- ===== CONTADOR: operacion contable diaria =====
                -- Plan de cuentas: crear y editar (NO eliminar - cuenta del PUC debe preservarse)
                ('CONTADOR', 'CFG.CUENTAS.CREAR'),
                ('CONTADOR', 'CFG.CUENTAS.EDITAR'),
                ('CONTADOR', 'CREATE_ACCOUNTING_ACCOUNT'),
                ('CONTADOR', 'UPDATE_ACCOUNTING_ACCOUNT'),
                ('CONTADOR', 'CREATE_CHART_OF_ACCOUNT'),
                ('CONTADOR', 'UPDATE_CHART_OF_ACCOUNT'),
                -- Contabilizar asientos + eliminar BORRADOR
                ('CONTADOR', 'POST_JOURNAL_ENTRY'),
                ('CONTADOR', 'CG.COMPROBANTES.CONTABILIZAR'),
                ('CONTADOR', 'CG.COMPROBANTES.ELIMINAR'),
                ('CONTADOR', 'DELETE_JOURNAL_ENTRY'),
                ('CONTADOR', 'VOID_JOURNAL_ENTRY'),
                -- BNK: registrar movimientos en cuentas
                ('CONTADOR', 'CREATE_FINANCIAL_MOVEMENT'),
                ('CONTADOR', 'BNK.MOVIMIENTOS.CREAR'),
                -- BNK: editar cuenta bancaria (contador maneja saldos, conciliaciones, mov)
                ('CONTADOR', 'BNK.CUENTAS.EDITAR'),
                ('CONTADOR', 'UPDATE_BANK_ACCOUNT'),
                -- BNK chequeras: toggle (cambiar estado)
                ('CONTADOR', 'BNK.CHEQUERAS.ELIMINAR'),
                ('CONTADOR', 'DELETE_CHECKBOOK'),
                -- AR: conciliar status
                ('CONTADOR', 'UPDATE_SALES_INVOICE'),
                -- AP: aplicar anticipos + conciliar pagos
                ('CONTADOR', 'AP.ANTICIPOS.APLICAR'),
                ('CONTADOR', 'AP.PAGOS.EDITAR'),
                ('CONTADOR', 'UPDATE_AP_PAYMENT'),

                -- ===== AUDITOR: validar contabilizacion (consulta los flujos) =====
                ('AUDITOR',  'POST_JOURNAL_ENTRY'),

                -- ===== AUXILIAR_CONTABLE: crear/editar cuentas (captura) =====
                ('AUXILIAR_CONTABLE', 'CFG.CUENTAS.CREAR'),
                ('AUXILIAR_CONTABLE', 'CFG.CUENTAS.EDITAR'),
                ('AUXILIAR_CONTABLE', 'CREATE_ACCOUNTING_ACCOUNT'),
                ('AUXILIAR_CONTABLE', 'UPDATE_ACCOUNTING_ACCOUNT'),
                ('AUXILIAR_CONTABLE', 'BNK.MOVIMIENTOS.CREAR'),
                ('AUXILIAR_CONTABLE', 'CREATE_FINANCIAL_MOVEMENT'),

                -- ===== TESORERO: ver movimientos + conciliar bancos =====
                ('TESORERO', 'BNK.MOVIMIENTOS.CREAR'),
                ('TESORERO', 'CREATE_FINANCIAL_MOVEMENT'),
                ('TESORERO', 'UPDATE_FINANCIAL_MOVEMENT'),
                ('TESORERO', 'BNK.MOVIMIENTOS.EDITAR'),
                ('TESORERO', 'BNK.CONCILIACION.CREAR'),
                ('TESORERO', 'BNK.CONCILIACION.VER'),
                ('TESORERO', 'CREATE_BANK_RECONCILIATION'),
                ('TESORERO', 'VIEW_BANK_RECONCILIATION')
            ) AS m(target_role, perm_code)
        LOOP
            IF rec.role_name = mapping.target_role THEN
                SELECT id INTO role_perm_id
                  FROM permissions
                 WHERE code = mapping.perm_code AND deleted_at IS NULL
                 LIMIT 1;
                IF role_perm_id IS NOT NULL THEN
                    INSERT INTO roles_permissions (role_id, permission_id)
                    SELECT rec.role_id, role_perm_id
                     WHERE NOT EXISTS (
                         SELECT 1 FROM roles_permissions
                          WHERE role_id = rec.role_id AND permission_id = role_perm_id
                     );
                END IF;
            END IF;
        END LOOP;
    END LOOP;
    RAISE NOTICE 'V9-Zzzzd: gaps operativos finales cerrados';
END $$;
