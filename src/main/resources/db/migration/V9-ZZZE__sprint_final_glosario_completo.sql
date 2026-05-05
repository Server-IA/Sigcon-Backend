-- Sprint Final: 86 permisos atomicos del glosario completo + asignaciones a roles
-- Idempotente. Aplica solo donde NOT EXISTS para no romper re-ejecucion.

DO $$
DECLARE
    v_au_module BIGINT;
    v_pa_module BIGINT;
    v_nom_module BIGINT;
    v_bnk_module BIGINT;
    v_cg_module BIGINT;
    v_int_module BIGINT;
    v_plat_module BIGINT;
    v_role_admin_id BIGINT;
    v_role_contador_id BIGINT;
    v_role_auditor_id BIGINT;
    v_role_aux_id BIGINT;
BEGIN
    SELECT id INTO v_pa_module FROM modules WHERE name ILIKE 'parametr%' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_au_module FROM modules WHERE name ILIKE 'auditor%' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_nom_module FROM modules WHERE name ILIKE 'nomin%' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_bnk_module FROM modules WHERE (name ILIKE '%banc%' OR name ILIKE '%caja%') AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_cg_module FROM modules WHERE (name ILIKE 'contabil%' OR name ILIKE '%general%') AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_int_module FROM modules WHERE name ILIKE '%integr%' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_plat_module FROM modules WHERE (name ILIKE 'parametr%') AND deleted_at IS NULL LIMIT 1;

    -- Fallback al modulo PA si alguno no existe
    IF v_au_module IS NULL THEN v_au_module := v_pa_module; END IF;
    IF v_nom_module IS NULL THEN v_nom_module := v_pa_module; END IF;
    IF v_bnk_module IS NULL THEN v_bnk_module := v_pa_module; END IF;
    IF v_cg_module IS NULL THEN v_cg_module := v_pa_module; END IF;
    IF v_int_module IS NULL THEN v_int_module := v_pa_module; END IF;
    IF v_plat_module IS NULL THEN v_plat_module := v_pa_module; END IF;

    -- ============ Insertar permisos NUEVOS ============
    INSERT INTO permissions(code, name, description, type, module_id, created_at, updated_at)
    SELECT v.code, v.name, v.description, v.ptype, v.mod, NOW(), NOW()
      FROM (VALUES
        -- AUDITORIA: Hallazgos, Reglas, Retencion
        ('AU.HALLAZGOS.VER',       'Ver hallazgos de auditoria (glosario)',          'HU-AU-08', 'READ',   v_au_module),
        ('AU.HALLAZGOS.CREAR',     'Crear hallazgo de auditoria (glosario)',         'HU-AU-08', 'CREATE', v_au_module),
        ('AU.HALLAZGOS.REVISAR',   'Revisar hallazgo de auditoria',       'HU-AU-08', 'UPDATE', v_au_module),
        ('AU.HALLAZGOS.CERRAR',    'Cerrar hallazgo de auditoria',        'HU-AU-08', 'UPDATE', v_au_module),
        ('AU.HALLAZGOS.ELIMINAR',  'Eliminar hallazgo de auditoria (glosario)',      'HU-AU-08', 'DELETE', v_au_module),
        ('AU.REGLAS.VER',          'Ver reglas de riesgo (glosario)',                'HU-AU-04', 'READ',   v_au_module),
        ('AU.REGLAS.CREAR',        'Crear regla de riesgo (glosario)',               'HU-AU-04', 'CREATE', v_au_module),
        ('AU.REGLAS.EDITAR',       'Editar regla de riesgo (glosario)',              'HU-AU-04', 'UPDATE', v_au_module),
        ('AU.REGLAS.TOGGLE',       'Activar/desactivar regla de riesgo',  'HU-AU-04', 'UPDATE', v_au_module),
        ('AU.REGLAS.ELIMINAR',     'Eliminar regla de riesgo (glosario)',            'HU-AU-04', 'DELETE', v_au_module),
        ('AU.RETENCION.VER',       'Ver politicas de retencion (glosario)',          'HU-AU-10', 'READ',   v_au_module),
        ('AU.RETENCION.CREAR',     'Crear politica de retencion (glosario)',         'HU-AU-10', 'CREATE', v_au_module),
        ('AU.RETENCION.EDITAR',    'Editar politica de retencion (glosario)',        'HU-AU-10', 'UPDATE', v_au_module),
        ('AU.RETENCION.ELIMINAR',  'Eliminar politica de retencion (glosario)',      'HU-AU-10', 'DELETE', v_au_module),
        ('AU.RETENCION.LEGAL_HOLD','Activar/liberar legal hold de logs',  'HU-AU-10', 'UPDATE', v_au_module),

        -- BANCOS Y CAJAS: subset operativo
        ('BNK.BANCOS.VER',         'Ver bancos (glosario)',                          'HU-BNK',   'READ',   v_bnk_module),
        ('BNK.BANCOS.CREAR',       'Crear banco (glosario)',                         'HU-BNK',   'CREATE', v_bnk_module),
        ('BNK.BANCOS.EDITAR',      'Editar banco (glosario)',                        'HU-BNK',   'UPDATE', v_bnk_module),
        ('BNK.BANCOS.ELIMINAR',    'Eliminar banco (glosario)',                      'HU-BNK',   'DELETE', v_bnk_module),
        ('BNK.SUCURSALES.VER',     'Ver sucursales bancarias (glosario)',            'HU-BNK',   'READ',   v_bnk_module),
        ('BNK.SUCURSALES.CREAR',   'Crear sucursal bancaria (glosario)',             'HU-BNK',   'CREATE', v_bnk_module),
        ('BNK.SUCURSALES.EDITAR',  'Editar sucursal bancaria (glosario)',            'HU-BNK',   'UPDATE', v_bnk_module),
        ('BNK.SUCURSALES.ELIMINAR','Eliminar sucursal bancaria (glosario)',          'HU-BNK',   'DELETE', v_bnk_module),
        ('BNK.CUENTAS.VER',        'Ver cuentas bancarias (glosario)',               'HU-BNK',   'READ',   v_bnk_module),
        ('BNK.CUENTAS.CREAR',      'Crear cuenta bancaria (glosario)',               'HU-BNK',   'CREATE', v_bnk_module),
        ('BNK.CUENTAS.EDITAR',     'Editar cuenta bancaria (glosario)',              'HU-BNK',   'UPDATE', v_bnk_module),
        ('BNK.CUENTAS.ELIMINAR',   'Eliminar cuenta bancaria (glosario)',            'HU-BNK',   'DELETE', v_bnk_module),
        ('BNK.CHEQUERAS.VER',      'Ver chequeras (glosario)',                       'HU-BNK',   'READ',   v_bnk_module),
        ('BNK.CHEQUERAS.CREAR',    'Crear chequera (glosario)',                      'HU-BNK',   'CREATE', v_bnk_module),
        ('BNK.CHEQUERAS.EDITAR',   'Editar chequera (glosario)',                     'HU-BNK',   'UPDATE', v_bnk_module),
        ('BNK.CHEQUERAS.ELIMINAR', 'Eliminar chequera (glosario)',                   'HU-BNK',   'DELETE', v_bnk_module),
        ('BNK.CHEQUES.VER',        'Ver cheques (glosario)',                         'HU-BNK',   'READ',   v_bnk_module),
        ('BNK.CHEQUES.EMITIR',     'Emitir cheque',                       'HU-BNK',   'CREATE', v_bnk_module),
        ('BNK.CHEQUES.COBRAR',     'Cobrar cheque',                       'HU-BNK',   'UPDATE', v_bnk_module),
        ('BNK.CHEQUES.ANULAR',     'Anular cheque',                       'HU-BNK',   'UPDATE', v_bnk_module),
        ('BNK.CAJAS.VER',          'Ver cajas (glosario)',                           'HU-BNK',   'READ',   v_bnk_module),
        ('BNK.CAJAS.CREAR',        'Crear caja (glosario)',                          'HU-BNK',   'CREATE', v_bnk_module),
        ('BNK.CAJAS.EDITAR',       'Editar caja (glosario)',                         'HU-BNK',   'UPDATE', v_bnk_module),
        ('BNK.CAJAS.ELIMINAR',     'Eliminar caja (glosario)',                       'HU-BNK',   'DELETE', v_bnk_module),
        ('BNK.ARQUEOS.VER',        'Ver arqueos de caja (glosario)',                 'HU-BNK',   'READ',   v_bnk_module),
        ('BNK.ARQUEOS.CREAR',      'Crear arqueo de caja (glosario)',                'HU-BNK',   'CREATE', v_bnk_module),
        ('BNK.ARQUEOS.EDITAR',     'Editar arqueo de caja (glosario)',               'HU-BNK',   'UPDATE', v_bnk_module),
        ('BNK.ARQUEOS.APROBAR',    'Aprobar/rechazar arqueo de caja',     'HU-BNK',   'UPDATE', v_bnk_module),
        ('BNK.ARQUEOS.ELIMINAR',   'Eliminar arqueo de caja (glosario)',             'HU-BNK',   'DELETE', v_bnk_module),
        ('BNK.MOVIMIENTOS.VER',    'Ver movimientos financieros (glosario)',         'HU-BNK',   'READ',   v_bnk_module),
        ('BNK.MOVIMIENTOS.CREAR',  'Crear movimiento financiero (glosario)',         'HU-BNK',   'CREATE', v_bnk_module),
        ('BNK.PROYECCIONES.VER',   'Ver proyecciones de flujo de caja (glosario)',   'HU-BNK',   'READ',   v_bnk_module),
        ('BNK.PROYECCIONES.CREAR', 'Crear proyeccion de flujo de caja (glosario)',   'HU-BNK',   'CREATE', v_bnk_module),
        ('BNK.PROYECCIONES.EDITAR','Editar proyeccion de flujo de caja (glosario)',  'HU-BNK',   'UPDATE', v_bnk_module),
        ('BNK.PROYECCIONES.APROBAR','Aprobar/ejecutar proyeccion',        'HU-BNK',   'UPDATE', v_bnk_module),
        ('BNK.PROYECCIONES.ELIMINAR','Eliminar proyeccion (glosario)',               'HU-BNK',   'DELETE', v_bnk_module),
        ('BNK.CONCILIACION.VER',   'Ver conciliacion bancaria (glosario)',           'HU-BNK',   'READ',   v_bnk_module),
        ('BNK.CONCILIACION.CREAR', 'Crear sesion de conciliacion (glosario)',        'HU-BNK',   'CREATE', v_bnk_module),
        ('BNK.CONCILIACION.EDITAR','Editar conciliacion (match/unmatch) (glosario)', 'HU-BNK',   'UPDATE', v_bnk_module),
        ('BNK.CONCILIACION.CERRAR','Cerrar sesion de conciliacion',       'HU-BNK',   'UPDATE', v_bnk_module),

        -- CONTABILIDAD GENERAL: comprobantes + libros + periodos
        ('CG.COMPROBANTES.VER',          'Ver comprobantes contables (glosario)',          'HU-CG', 'READ',   v_cg_module),
        ('CG.COMPROBANTES.CREAR',        'Crear comprobante contable (glosario)',          'HU-CG', 'CREATE', v_cg_module),
        ('CG.COMPROBANTES.EDITAR',       'Editar comprobante contable (glosario)',         'HU-CG', 'UPDATE', v_cg_module),
        ('CG.COMPROBANTES.CONTABILIZAR', 'Contabilizar (POSTED)',               'HU-CG', 'UPDATE', v_cg_module),
        ('CG.COMPROBANTES.REVERSAR',     'Reversar comprobante contable',       'HU-CG', 'UPDATE', v_cg_module),
        ('CG.COMPROBANTES.ELIMINAR',     'Eliminar comprobante (DRAFT) (glosario)',        'HU-CG', 'DELETE', v_cg_module),
        ('CG.COMPROBANTES.EXPORTAR',     'Exportar comprobante PDF/XLSX',       'HU-CG', 'READ',   v_cg_module),
        ('CG.LIBROS.VER',                'Ver libros oficiales (Diario/Mayor) (glosario)', 'HU-CG', 'READ',   v_cg_module),
        ('CG.PERIODOS.VER',              'Ver periodos contables (glosario)',              'HU-CG', 'READ',   v_cg_module),
        ('CG.PERIODOS.CREAR',            'Crear periodo contable (glosario)',              'HU-CG', 'CREATE', v_cg_module),
        ('CG.PERIODOS.EDITAR',           'Editar periodo contable (glosario)',             'HU-CG', 'UPDATE', v_cg_module),
        ('CG.PERIODOS.CERRAR',           'Cerrar/reabrir/bloquear periodo',     'HU-CG', 'UPDATE', v_cg_module),

        -- INTEGRACION
        ('INT.MONITOREO.VER',      'Ver monitoreo de integracion AAEF (glosario)',   'HU-INT',   'READ',   v_int_module),

        -- NOMINA: empleados, conceptos, prestaciones
        ('NOM.EMPLEADOS.VER',      'Ver empleados (glosario)',                       'HU-NOM-01', 'READ',   v_nom_module),
        ('NOM.EMPLEADOS.CREAR',    'Crear empleado (glosario)',                      'HU-NOM-01', 'CREATE', v_nom_module),
        ('NOM.EMPLEADOS.EDITAR',   'Editar empleado (glosario)',                     'HU-NOM-01', 'UPDATE', v_nom_module),
        ('NOM.EMPLEADOS.ELIMINAR', 'Eliminar empleado (glosario)',                   'HU-NOM-01', 'DELETE', v_nom_module),
        ('NOM.CONCEPTOS.VER',      'Ver conceptos de nomina (glosario)',             'HU-NOM-02', 'READ',   v_nom_module),
        ('NOM.CONCEPTOS.CREAR',    'Crear concepto de nomina (glosario)',            'HU-NOM-02', 'CREATE', v_nom_module),
        ('NOM.CONCEPTOS.EDITAR',   'Editar concepto de nomina (glosario)',           'HU-NOM-02', 'UPDATE', v_nom_module),
        ('NOM.CONCEPTOS.ELIMINAR', 'Eliminar concepto de nomina (glosario)',         'HU-NOM-02', 'DELETE', v_nom_module),
        ('NOM.PRESTACIONES.VER',   'Ver prestaciones sociales (glosario)',           'HU-NOM-05', 'READ',   v_nom_module),
        ('NOM.PRESTACIONES.CALCULAR','Calcular prestaciones sociales',    'HU-NOM-05', 'UPDATE', v_nom_module),

        -- PLATAFORMA (operaciones cross-tenant)
        ('PLAT.EMPRESAS.VER',      'Ver empresas (plataforma) (glosario)',           'HU-PA-PLAT-01', 'READ',   v_plat_module),
        ('PLAT.EMPRESAS.CREAR',    'Crear empresa (glosario)',                       'HU-PA-PLAT-02', 'CREATE', v_plat_module),
        ('PLAT.EMPRESAS.EDITAR',   'Editar empresa (glosario)',                      'HU-PA-PLAT-01', 'UPDATE', v_plat_module),
        ('PLAT.EMPRESAS.ACTIVAR',  'Activar empresa (glosario)',                     'HU-PA-PLAT-05', 'UPDATE', v_plat_module),
        ('PLAT.EMPRESAS.DESACTIVAR','Desactivar empresa (glosario)',                 'HU-PA-PLAT-05', 'UPDATE', v_plat_module),
        ('PLAT.USUARIOS.VER',      'Ver usuarios cross-empresa (glosario)',          'HU-PA-PLAT-04', 'READ',   v_plat_module),
        ('PLAT.USUARIOS.RESET_PASSWORD','Reset password admin',           'HU-PA-PLAT-04', 'UPDATE', v_plat_module),
        ('PLAT.DASHBOARD.VER',     'Ver dashboard de plataforma (glosario)',         'HU-PA-PLAT-06', 'READ',   v_plat_module)
      ) AS v(code, name, description, ptype, mod)
     WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code AND p.deleted_at IS NULL);

    -- ============ Resolver IDs de roles ============
    SELECT id INTO v_role_admin_id FROM roles WHERE name='ADMIN_EMPRESA' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_role_contador_id FROM roles WHERE name='CONTADOR' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_role_auditor_id FROM roles WHERE name='AUDITOR' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_role_aux_id FROM roles WHERE name='AUXILIAR_CONTABLE' AND deleted_at IS NULL LIMIT 1;

    -- ADMIN_EMPRESA recibe TODOS los nuevos
    IF v_role_admin_id IS NOT NULL THEN
        INSERT INTO roles_permissions(role_id, permission_id)
        SELECT v_role_admin_id, p.id FROM permissions p
         WHERE p.deleted_at IS NULL
           AND p.code IN (
             'AU.HALLAZGOS.VER','AU.HALLAZGOS.CREAR','AU.HALLAZGOS.REVISAR','AU.HALLAZGOS.CERRAR','AU.HALLAZGOS.ELIMINAR',
             'AU.REGLAS.VER','AU.REGLAS.CREAR','AU.REGLAS.EDITAR','AU.REGLAS.TOGGLE','AU.REGLAS.ELIMINAR',
             'AU.RETENCION.VER','AU.RETENCION.CREAR','AU.RETENCION.EDITAR','AU.RETENCION.ELIMINAR','AU.RETENCION.LEGAL_HOLD',
             'BNK.BANCOS.VER','BNK.BANCOS.CREAR','BNK.BANCOS.EDITAR','BNK.BANCOS.ELIMINAR',
             'BNK.SUCURSALES.VER','BNK.SUCURSALES.CREAR','BNK.SUCURSALES.EDITAR','BNK.SUCURSALES.ELIMINAR',
             'BNK.CUENTAS.VER','BNK.CUENTAS.CREAR','BNK.CUENTAS.EDITAR','BNK.CUENTAS.ELIMINAR',
             'BNK.CHEQUERAS.VER','BNK.CHEQUERAS.CREAR','BNK.CHEQUERAS.EDITAR','BNK.CHEQUERAS.ELIMINAR',
             'BNK.CHEQUES.VER','BNK.CHEQUES.EMITIR','BNK.CHEQUES.COBRAR','BNK.CHEQUES.ANULAR',
             'BNK.CAJAS.VER','BNK.CAJAS.CREAR','BNK.CAJAS.EDITAR','BNK.CAJAS.ELIMINAR',
             'BNK.ARQUEOS.VER','BNK.ARQUEOS.CREAR','BNK.ARQUEOS.EDITAR','BNK.ARQUEOS.APROBAR','BNK.ARQUEOS.ELIMINAR',
             'BNK.MOVIMIENTOS.VER','BNK.MOVIMIENTOS.CREAR',
             'BNK.PROYECCIONES.VER','BNK.PROYECCIONES.CREAR','BNK.PROYECCIONES.EDITAR','BNK.PROYECCIONES.APROBAR','BNK.PROYECCIONES.ELIMINAR',
             'BNK.CONCILIACION.VER','BNK.CONCILIACION.CREAR','BNK.CONCILIACION.EDITAR','BNK.CONCILIACION.CERRAR',
             'CG.COMPROBANTES.VER','CG.COMPROBANTES.CREAR','CG.COMPROBANTES.EDITAR','CG.COMPROBANTES.CONTABILIZAR',
             'CG.COMPROBANTES.REVERSAR','CG.COMPROBANTES.ELIMINAR','CG.COMPROBANTES.EXPORTAR',
             'CG.LIBROS.VER','CG.PERIODOS.VER','CG.PERIODOS.CREAR','CG.PERIODOS.EDITAR','CG.PERIODOS.CERRAR',
             'INT.MONITOREO.VER',
             'NOM.EMPLEADOS.VER','NOM.EMPLEADOS.CREAR','NOM.EMPLEADOS.EDITAR','NOM.EMPLEADOS.ELIMINAR',
             'NOM.CONCEPTOS.VER','NOM.CONCEPTOS.CREAR','NOM.CONCEPTOS.EDITAR','NOM.CONCEPTOS.ELIMINAR',
             'NOM.PRESTACIONES.VER','NOM.PRESTACIONES.CALCULAR'
           )
         ON CONFLICT DO NOTHING;
    END IF;

    -- CONTADOR: operativo (CRUD basicos sin DELETE de cosas criticas, sin admin AU/PLAT)
    IF v_role_contador_id IS NOT NULL THEN
        INSERT INTO roles_permissions(role_id, permission_id)
        SELECT v_role_contador_id, p.id FROM permissions p
         WHERE p.deleted_at IS NULL
           AND p.code IN (
             'BNK.BANCOS.VER','BNK.SUCURSALES.VER',
             'BNK.CUENTAS.VER','BNK.CUENTAS.CREAR','BNK.CUENTAS.EDITAR',
             'BNK.CHEQUERAS.VER','BNK.CHEQUERAS.CREAR','BNK.CHEQUERAS.EDITAR',
             'BNK.CHEQUES.VER','BNK.CHEQUES.EMITIR','BNK.CHEQUES.COBRAR','BNK.CHEQUES.ANULAR',
             'BNK.CAJAS.VER','BNK.CAJAS.CREAR','BNK.CAJAS.EDITAR',
             'BNK.ARQUEOS.VER','BNK.ARQUEOS.CREAR','BNK.ARQUEOS.EDITAR',
             'BNK.MOVIMIENTOS.VER','BNK.MOVIMIENTOS.CREAR',
             'BNK.PROYECCIONES.VER','BNK.PROYECCIONES.CREAR','BNK.PROYECCIONES.EDITAR',
             'BNK.CONCILIACION.VER','BNK.CONCILIACION.CREAR','BNK.CONCILIACION.EDITAR','BNK.CONCILIACION.CERRAR',
             'CG.COMPROBANTES.VER','CG.COMPROBANTES.CREAR','CG.COMPROBANTES.EDITAR','CG.COMPROBANTES.CONTABILIZAR',
             'CG.COMPROBANTES.REVERSAR','CG.COMPROBANTES.EXPORTAR',
             'CG.LIBROS.VER','CG.PERIODOS.VER',
             'NOM.EMPLEADOS.VER','NOM.EMPLEADOS.CREAR','NOM.EMPLEADOS.EDITAR',
             'NOM.CONCEPTOS.VER',
             'NOM.PRESTACIONES.VER','NOM.PRESTACIONES.CALCULAR'
           )
         ON CONFLICT DO NOTHING;
    END IF;

    -- AUDITOR: solo lectura global + auditoria especializada
    IF v_role_auditor_id IS NOT NULL THEN
        INSERT INTO roles_permissions(role_id, permission_id)
        SELECT v_role_auditor_id, p.id FROM permissions p
         WHERE p.deleted_at IS NULL
           AND (
             p.code LIKE '%.VER'
             OR p.code IN ('AU.HALLAZGOS.CREAR','AU.HALLAZGOS.REVISAR','AU.HALLAZGOS.CERRAR',
                           'AU.RETENCION.LEGAL_HOLD',
                           'CG.COMPROBANTES.EXPORTAR')
           )
         ON CONFLICT DO NOTHING;
    END IF;

    -- AUXILIAR_CONTABLE: subset basico de captura
    IF v_role_aux_id IS NOT NULL THEN
        INSERT INTO roles_permissions(role_id, permission_id)
        SELECT v_role_aux_id, p.id FROM permissions p
         WHERE p.deleted_at IS NULL
           AND p.code IN (
             'BNK.BANCOS.VER','BNK.CUENTAS.VER','BNK.CHEQUES.VER','BNK.CAJAS.VER','BNK.MOVIMIENTOS.VER',
             'CG.COMPROBANTES.VER','CG.LIBROS.VER','CG.PERIODOS.VER',
             'NOM.EMPLEADOS.VER','NOM.CONCEPTOS.VER','NOM.PRESTACIONES.VER'
           )
         ON CONFLICT DO NOTHING;
    END IF;
END $$;
