-- ============================================================================
-- V9-ZZZZJ : Bloque AY+ - Cierre exhaustivo de gaps operativos en roles
-- Fecha: 2026-05-16
--
-- HALLAZGOS de auditoria 7 roles x 317 endpoints:
-- Tras V9-ZZZZI, CONTADOR pasa 62% (187/301). Los 114 restantes incluyen:
--   - ~50 endpoints administrativos (audit, roles, users, platform) -> 403 correcto
--   - ~25 endpoints de configuracion (catalogos pais/municipios/reportes/identidad) -> 403 correcto
--   - ~40 endpoints OPERATIVOS criticos donde CONTADOR si debe pasar:
--     * DIAN (resolutions VER/CREAR/EDITAR/ELIMINAR, factura electronica)
--     * Identidad visual VER (para ver branding de la empresa)
--     * Reportes (tipos/plantillas) VER
--     * Parametros VER
--     * Catalogos compartidos VER (paises, municipios)
--
-- AUDITOR: solo lectura amplia (todos los VER).
-- AUXILIAR_CONTABLE: lectura operativa basica.
-- TESORERO: lectura del area contable + nomina basica para conciliacion.
-- OPERADOR_NOMINA: lectura del area NOM + bancos para pagos.
--
-- Esta migracion CIERRA los gaps detectados, manteniendo segregacion:
-- gestion de roles/users/admin/platform sigue en SUPER+ADMIN_EMPRESA.
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
           AND r.name IN ('CONTADOR', 'AUDITOR', 'AUXILIAR_CONTABLE', 'TESORERO', 'OPERADOR_NOMINA')
    LOOP
        FOR mapping IN
            SELECT * FROM (VALUES
                -- ===== CONTADOR: factura electronica, branding, reportes, catalogos =====
                -- DIAN resolutions + facturacion electronica
                ('CONTADOR', 'AR.RESOLUCIONES_DIAN.VER'),
                ('CONTADOR', 'AR.RESOLUCIONES_DIAN.CREAR'),
                ('CONTADOR', 'AR.RESOLUCIONES_DIAN.EDITAR'),
                ('CONTADOR', 'AR.RESOLUCIONES_DIAN.ELIMINAR'),
                ('CONTADOR', 'AR.DIAN.GENERAR'),
                ('CONTADOR', 'READ_DIAN_RESOLUTION'),
                ('CONTADOR', 'VIEW_DIAN_RESOLUTION'),
                ('CONTADOR', 'CREATE_DIAN_RESOLUTION'),
                ('CONTADOR', 'UPDATE_DIAN_RESOLUTION'),
                ('CONTADOR', 'DELETE_DIAN_RESOLUTION'),
                ('CONTADOR', 'READ_DIAN'),
                ('CONTADOR', 'READ_DIAN_REPORT'),
                ('CONTADOR', 'CREATE_DIAN_XML'),
                ('CONTADOR', 'SUBMIT_DIAN'),
                -- Identidad visual + branding VER (para ver, no editar)
                ('CONTADOR', 'PAR.IDENTIDAD_VISUAL.VER'),
                -- Catalogos compartidos VER (paises, municipios)
                ('CONTADOR', 'VIEW_COUNTRY'),
                ('CONTADOR', 'VIEW_MUNICIPALITY'),
                -- Plantillas / tipos de reporte VER
                ('CONTADOR', 'VIEW_REPORT_TEMPLATE'),
                ('CONTADOR', 'VIEW_REPORT_TYPE'),
                ('CONTADOR', 'PAR.REPORTES_PLANTILLAS.VER'),
                ('CONTADOR', 'PAR.REPORTES_TIPOS.VER'),
                -- Parametros VER
                ('CONTADOR', 'PAR.PARAMETROS.VER'),
                ('CONTADOR', 'VIEW_PARAMETER'),
                -- Retencion del sistema VER
                ('CONTADOR', 'VIEW_WITHHOLDING_ASSIGNMENT'),
                -- Navegacion VER (ver su propia navegacion)
                ('CONTADOR', 'PAR.NAVEGACION.VER'),

                -- ===== AUDITOR: lectura amplia =====
                ('AUDITOR',  'AR.RESOLUCIONES_DIAN.VER'),
                ('AUDITOR',  'AR.DIAN.GENERAR'),
                ('AUDITOR',  'READ_DIAN_RESOLUTION'),
                ('AUDITOR',  'VIEW_DIAN_RESOLUTION'),
                ('AUDITOR',  'READ_DIAN'),
                ('AUDITOR',  'READ_DIAN_REPORT'),
                ('AUDITOR',  'PAR.IDENTIDAD_VISUAL.VER'),
                ('AUDITOR',  'VIEW_COUNTRY'),
                ('AUDITOR',  'VIEW_MUNICIPALITY'),
                ('AUDITOR',  'VIEW_REPORT_TEMPLATE'),
                ('AUDITOR',  'VIEW_REPORT_TYPE'),
                ('AUDITOR',  'PAR.REPORTES_PLANTILLAS.VER'),
                ('AUDITOR',  'PAR.REPORTES_TIPOS.VER'),
                ('AUDITOR',  'PAR.PARAMETROS.VER'),
                ('AUDITOR',  'VIEW_PARAMETER'),
                ('AUDITOR',  'VIEW_WITHHOLDING_ASSIGNMENT'),
                ('AUDITOR',  'PAR.NAVEGACION.VER'),

                -- ===== AUXILIAR_CONTABLE: lectura operativa basica =====
                ('AUXILIAR_CONTABLE', 'VIEW_COUNTRY'),
                ('AUXILIAR_CONTABLE', 'VIEW_MUNICIPALITY'),
                ('AUXILIAR_CONTABLE', 'PAR.IDENTIDAD_VISUAL.VER'),
                ('AUXILIAR_CONTABLE', 'PAR.NAVEGACION.VER'),
                ('AUXILIAR_CONTABLE', 'AR.RESOLUCIONES_DIAN.VER'),
                ('AUXILIAR_CONTABLE', 'READ_DIAN_RESOLUTION'),

                -- ===== TESORERO: lectura contable + catalogos =====
                ('TESORERO', 'VIEW_COUNTRY'),
                ('TESORERO', 'VIEW_MUNICIPALITY'),
                ('TESORERO', 'PAR.IDENTIDAD_VISUAL.VER'),
                ('TESORERO', 'PAR.NAVEGACION.VER'),
                ('TESORERO', 'CG.COMPROBANTES.VER'),
                ('TESORERO', 'VIEW_JOURNAL_ENTRY'),
                ('TESORERO', 'CG.PERIODOS.VER'),
                ('TESORERO', 'VIEW_ACCOUNTING_PERIOD'),
                ('TESORERO', 'AR.RESOLUCIONES_DIAN.VER'),

                -- ===== OPERADOR_NOMINA: catalogos + branding =====
                ('OPERADOR_NOMINA', 'PAR.IDENTIDAD_VISUAL.VER'),
                ('OPERADOR_NOMINA', 'PAR.NAVEGACION.VER'),
                ('OPERADOR_NOMINA', 'VIEW_COUNTRY'),
                ('OPERADOR_NOMINA', 'VIEW_MUNICIPALITY'),
                ('OPERADOR_NOMINA', 'CFG.CENTROS_COSTO.VER')
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
    RAISE NOTICE 'V9-ZZZZJ: permisos operativos asignados a CONTADOR/AUDITOR/AUX/TES/OPN';
END $$;
