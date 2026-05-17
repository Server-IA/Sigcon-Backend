-- ============================================================================
-- V9-Zzzzb : Bloque AY - Completar permisos faltantes en roles predefinidos
-- Fecha: 2026-05-16 (renombrada 2026-05-17, antes V9-ZZZZI)
-- Renombre forzado por bug de orden lexical (ver V9-Zzzza header).
--
-- HALLAZGOS DE AUDITORIA (matriz roles x endpoints):
-- El CONTADOR predefinido (creado por V9-J / _seed_predefined_roles_per_tenant)
-- TIENE 135 permisos formato MOD.ENTIDAD.ACCION pero le FALTAN permisos clave:
--   - VIEW_ACCOUNTING_ACCOUNT  (plan de cuentas)
--   - PAR.USUARIOS.VER / VIEW_USER  (lista de usuarios de su empresa)
--   - NOM.EMPLEADOS.VER  (empleados de nomina)
--   - Codes equivalentes legacy de varios endpoints
--
-- Asi que aunque el endpoint @PreAuthorize aceptaba PERM_VIEW_ACCOUNTING_ACCOUNT,
-- el CONTADOR no podia consumirlo. El sintoma:
-- "a veces funciona, a veces no" reportado por el usuario QA.
--
-- FIX (esta migracion):
--   1. Asigna a CONTADOR los permisos faltantes en BD (legacy + nuevo).
--   2. Asigna a AUDITOR (solo lectura) los VIEW_* faltantes.
--   3. Asigna a AUXILIAR_CONTABLE permisos basicos de captura.
--   4. Idempotente con ON CONFLICT/NOT EXISTS.
-- ============================================================================

DO $$
DECLARE
    rec RECORD;
    mapping RECORD;
    role_perm_id BIGINT;
BEGIN
    -- Itera todos los roles CONTADOR de cada empresa (multi-tenant)
    FOR rec IN
        SELECT r.id AS role_id, r.name AS role_name
          FROM roles r
         WHERE r.deleted_at IS NULL
           AND r.name IN ('CONTADOR', 'AUDITOR', 'AUXILIAR_CONTABLE', 'TESORERO', 'OPERADOR_NOMINA')
    LOOP
        FOR mapping IN
            SELECT * FROM (VALUES
                -- (rol, code permiso)
                -- CONTADOR: necesita ver plan de cuentas, usuarios de su empresa, y empleados
                ('CONTADOR', 'VIEW_ACCOUNTING_ACCOUNT'),
                ('CONTADOR', 'PAR.USUARIOS.VER'),
                ('CONTADOR', 'VIEW_USER'),
                ('CONTADOR', 'NOM.EMPLEADOS.VER'),
                -- AUDITOR: solo lectura, agrega lo faltante
                ('AUDITOR',  'VIEW_ACCOUNTING_ACCOUNT'),
                ('AUDITOR',  'PAR.USUARIOS.VER'),
                ('AUDITOR',  'VIEW_USER'),
                ('AUDITOR',  'NOM.EMPLEADOS.VER'),
                ('AUDITOR',  'CG.COMPROBANTES.VER'),
                ('AUDITOR',  'CG.LIBROS.VER'),
                ('AUDITOR',  'CG.LIBRO_DIARIO.VER'),
                ('AUDITOR',  'CG.LIBRO_MAYOR.VER'),
                ('AUDITOR',  'CG.PERIODOS.VER'),
                ('AUDITOR',  'CG.ESTADOS_FINANCIEROS.VER'),
                ('AUDITOR',  'CG.REPORTES.VER'),
                ('AUDITOR',  'ACT.ACTIVOS.VER'),
                ('AUDITOR',  'AP.FACTURAS_COMPRA.VER'),
                ('AUDITOR',  'AP.PAGOS.VER'),
                ('AUDITOR',  'AP.OC.VER'),
                ('AUDITOR',  'AR.FACTURAS_VENTA.VER'),
                ('AUDITOR',  'AR.COBROS.VER'),
                ('AUDITOR',  'BNK.CUENTAS.VER'),
                ('AUDITOR',  'BNK.CHEQUES.VER'),
                ('AUDITOR',  'BNK.MOVIMIENTOS.VER'),
                ('AUDITOR',  'CFG.DEPRECIACION.VER'),
                ('AUDITOR',  'CFG.REGLAS_TRIBUTARIAS.VER'),
                ('AUDITOR',  'TER.TERCEROS.VER'),
                ('AUDITOR',  'VIEW_EXCHANGE_RATES'),
                ('AUDITOR',  'VIEW_CURRENCY_TYPE'),
                ('AUXILIAR_CONTABLE', 'VIEW_EXCHANGE_RATES'),
                ('AUXILIAR_CONTABLE', 'VIEW_CURRENCY_TYPE'),
                -- Bloque AY: PUC y catalogo cuentas - CONTADOR/AUX/AUD necesitan ver el plan de cuentas
                ('CONTADOR', 'VIEW_CHART_OF_ACCOUNT'),
                ('AUDITOR',  'VIEW_CHART_OF_ACCOUNT'),
                ('AUXILIAR_CONTABLE', 'VIEW_CHART_OF_ACCOUNT'),
                -- Bloque AY: alertas AP y segmentacion - CONTADOR las usa para cartera
                ('CONTADOR', 'VIEW_AP_INVOICE'),
                ('CONTADOR', 'TER.SEGMENTACION.VER'),
                ('CONTADOR', 'TER.RIESGO.VER'),
                ('CONTADOR', 'VIEW_ECL_SEGMENT'),
                ('AUDITOR',  'TER.SEGMENTACION.VER'),
                ('AUDITOR',  'TER.RIESGO.VER'),
                ('AUDITOR',  'VIEW_ECL_SEGMENT'),
                ('AUXILIAR_CONTABLE', 'TER.SEGMENTACION.VER'),
                ('AUXILIAR_CONTABLE', 'VIEW_ECL_SEGMENT'),
                -- Bloque AY: NOM conceptos - CONTADOR necesita verlos cuando consulta nominas
                ('CONTADOR', 'NOM.CONCEPTOS.VER'),
                ('AUDITOR',  'NOM.CONCEPTOS.VER'),
                ('AUXILIAR_CONTABLE', 'NOM.CONCEPTOS.VER'),
                -- Bloque AY: AR alertas y reportes upcoming/overdue para CONTADOR
                ('CONTADOR', 'VIEW_SALES_INVOICE'),
                ('AUDITOR',  'VIEW_SALES_INVOICE'),
                -- Bloque AY: TESORERO necesita ver cuentas para conciliar
                ('TESORERO', 'PAR.USUARIOS.VER'),
                ('TESORERO', 'VIEW_USER'),
                ('TESORERO', 'VIEW_CHART_OF_ACCOUNT'),
                ('TESORERO', 'VIEW_ACCOUNTING_ACCOUNT'),
                ('TESORERO', 'VIEW_EXCHANGE_RATES'),
                -- Bloque AY: OPERADOR_NOMINA necesita ver empleados/recibos/terceros basicos
                ('OPERADOR_NOMINA', 'NOM.EMPLEADOS.VER'),
                ('OPERADOR_NOMINA', 'NOM.RECIBOS.VER'),
                ('OPERADOR_NOMINA', 'NOM.LIQUIDACION.VER'),
                ('OPERADOR_NOMINA', 'NOM.CONCEPTOS.VER'),
                ('OPERADOR_NOMINA', 'TER.TERCEROS.VER'),
                ('OPERADOR_NOMINA', 'VIEW_THIRD_PARTY'),
                ('OPERADOR_NOMINA', 'PAR.USUARIOS.VER'),
                ('OPERADOR_NOMINA', 'VIEW_USER'),
                -- AUXILIAR_CONTABLE: lectura amplia + captura basica
                ('AUXILIAR_CONTABLE', 'VIEW_ACCOUNTING_ACCOUNT'),
                ('AUXILIAR_CONTABLE', 'PAR.USUARIOS.VER'),
                ('AUXILIAR_CONTABLE', 'VIEW_USER'),
                ('AUXILIAR_CONTABLE', 'NOM.EMPLEADOS.VER'),
                ('AUXILIAR_CONTABLE', 'NOM.RECIBOS.VER'),
                ('AUXILIAR_CONTABLE', 'NOM.LIQUIDACION.VER'),
                ('AUXILIAR_CONTABLE', 'CFG.DEPRECIACION.VER'),
                ('AUXILIAR_CONTABLE', 'CFG.REGLAS_TRIBUTARIAS.VER'),
                ('AUXILIAR_CONTABLE', 'CFG.TASA_CAMBIO.VER')
            ) AS m(target_role, perm_code)
        LOOP
            IF rec.role_name = mapping.target_role THEN
                -- Buscar id del permiso
                SELECT id INTO role_perm_id
                  FROM permissions
                 WHERE code = mapping.perm_code AND deleted_at IS NULL
                 LIMIT 1;
                IF role_perm_id IS NOT NULL THEN
                    -- Asignar al rol si no lo tiene
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
    RAISE NOTICE 'V9-Zzzzb: permisos asignados a CONTADOR/AUDITOR/AUXILIAR completados';
END $$;
