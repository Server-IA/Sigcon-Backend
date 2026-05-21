-- ============================================================================
-- V9-Zzzzf : Filtrar submenus del sidebar por permiso requerido (Bug #3 QA)
-- Fecha: 2026-05-17 (Bloque AX)
--
-- BUG REPORTADO:
-- "Si asigno UN permiso de centros de costo, aparecen TODOS los submodulos de
--  Listas Contables (PUC, Cuentas, Tasas Cambio, Monedas, ...)"
--
-- CAUSA: MenuService.getMenusByModuleId filtra por roles (menu_permissions tabla),
-- pero esa tabla esta vacia, asi que TODOS los menus son tratados como publicos
-- y se muestran al usuario apenas tenga 1 perm del modulo.
--
-- FIX: agregar columna menus.required_permission_code con el code del permiso
-- requerido para que el menu sea visible. NULL = publico (compat).
-- MenuService aplica filtro adicional en runtime contra los perms del usuario.
-- ============================================================================

ALTER TABLE menus ADD COLUMN IF NOT EXISTS required_permission_code VARCHAR(120) NULL;

CREATE INDEX IF NOT EXISTS idx_menus_required_perm ON menus(required_permission_code)
  WHERE required_permission_code IS NOT NULL;

-- Mapping component -> permission_code (basado en convencion semantica)
UPDATE menus SET required_permission_code = CASE component
    -- Parametrizacion (modulo 1) - mayoria solo para PLATFORM_ADMIN
    WHEN 'PERFIL'              THEN NULL  -- publico
    WHEN 'MODULOS'             THEN 'PAR.MODULOS.VER'
    WHEN 'MENUS'               THEN 'PAR.MENUS.VER'
    WHEN 'PERMISSIONS'         THEN 'PAR.PERMISOS.VER'
    WHEN 'ROLES'               THEN 'PAR.ROLES.VER'
    WHEN 'USERS'               THEN 'PAR.USUARIOS.VER'
    WHEN 'PARAMETROS'          THEN 'PAR.PARAMETROS.VER'
    WHEN 'MENUSPERMISSIONS'    THEN 'PAR.MENUS.VER'
    WHEN 'PAISES'              THEN NULL  -- catalogo global
    WHEN 'MUNICIPIOS'          THEN NULL
    WHEN 'REPORT_TYPES'        THEN 'PAR.REPORTES_TIPOS.VER'
    WHEN 'REPORT_TEMPLATES'    THEN 'PAR.REPORTES_PLANTILLAS.VER'
    WHEN 'SYSTEM_WITHHOLDINGS' THEN NULL
    WHEN 'TEMPORARY_PERMISSIONS' THEN 'PAR.PERMISOS_TEMPORALES.VER'
    WHEN 'IDENTIDAD_VISUAL'    THEN 'PAR.IDENTIDAD_VISUAL.VER'
    -- QA Bloque BO (2026-05-18): los codes huerfanos PAR.NAVEGACION.VER y
    -- PAR.NOTIFICACIONES_ROL.VER nunca existieron. Los reales sembrados por
    -- V9-ZZW son PAR.NAVEGACION.EDITAR y PAR.NOTIFICACIONES.CONFIGURAR_ROL.
    -- Con codes huerfanos el filtro de MenuService.getModulesMenu ocultaba
    -- estos 2 menus para todo non-admin (incluido ADMIN_EMPRESA).
    WHEN 'NAVEGACION'          THEN 'PAR.NAVEGACION.EDITAR'
    WHEN 'NOTIFICACIONES_ROL'  THEN 'PAR.NOTIFICACIONES.CONFIGURAR_ROL'
    -- Listas Contables (modulo 2)
    WHEN 'PUC'                 THEN 'CFG.CUENTAS.VER'
    WHEN 'DEPRECIATION_RULES'  THEN 'CFG.DEPRECIACION.VER'
    WHEN 'CENTROS_COSTO'       THEN 'CFG.CENTROS_COSTO.VER'
    WHEN 'EXCHANGE_RATE'       THEN 'CFG.TASA_CAMBIO.VER'
    WHEN 'CURRENCY_TYPES'      THEN 'CFG.MONEDAS.VER'
    WHEN 'RULES_TAX'           THEN 'CFG.REGLAS_TRIBUTARIAS.VER'
    WHEN 'CUENTAS_CONTABLES'   THEN 'CFG.CUENTAS.VER'
    -- Activos (modulo 3)
    WHEN 'ASSETS_REGISTRY'     THEN 'ACT.ACTIVOS.VER'
    WHEN 'ACT_CALCULO_DEPRECIACION' THEN 'ACT.ACTIVOS.EJECUTAR_DEPRECIACION'
    WHEN 'CREATE_ASSETS'       THEN 'ACT.ACTIVOS.CREAR'
    WHEN 'UPDATE_ASSETS'       THEN 'ACT.ACTIVOS.EDITAR'
    WHEN 'ACT_GENERACION_INFORMES' THEN 'ACT.ACTIVOS.EXPORTAR_REPORTE'
    WHEN 'NIIF_VERIFICATION'   THEN 'ACT.ACTIVOS.VER'
    WHEN 'ACT_BAJAS_TRANSFERENCIAS' THEN 'ACT.ACTIVOS.DAR_DE_BAJA'
    -- Terceros (modulo 4)
    WHEN 'THIRD_PARTY_LIST'    THEN 'TER.TERCEROS.VER'
    WHEN 'SEGMENTATION'        THEN 'TER.SEGMENTACION.VER'
    WHEN 'COMMERCIAL_DATA'     THEN 'TER.DATOS_COMERCIALES.VER'
    -- Bancos y Cajas (modulo 5)
    WHEN 'CASH_LIST'           THEN 'BNK.CAJAS.VER'
    WHEN 'CHEQUERAS'           THEN 'BNK.CHEQUERAS.VER'
    WHEN 'CHEQUES'             THEN 'BNK.CHEQUES.VER'
    WHEN 'CATALOGO_BANCOS'     THEN 'BNK.BANCOS.VER'
    WHEN 'SUCURSALES_BANCARIAS' THEN 'BNK.SUCURSALES.VER'
    WHEN 'BANK_ACCOUNTS'       THEN 'BNK.CUENTAS.VER'
    WHEN 'CASH_FLOW_PROJECTIONS' THEN 'BNK.PROYECCIONES.VER'
    WHEN 'CASH_AUDITS'         THEN 'BNK.ARQUEOS.VER'
    WHEN 'FINANCIAL_MOVEMENTS' THEN 'BNK.MOVIMIENTOS.VER'
    -- Plataforma (modulo 6) - solo PLATFORM_ADMIN
    WHEN 'PLATFORM_DASHBOARD'  THEN NULL
    WHEN 'PLATFORM_EMPRESAS'   THEN NULL
    -- Cuentas por Pagar (modulo 7)
    WHEN 'AP_INVOICES'         THEN 'AP.FACTURAS_COMPRA.VER'
    WHEN 'AP_PAYMENTS'         THEN 'AP.PAGOS.VER'
    WHEN 'AP_ADVANCES'         THEN 'AP.ANTICIPOS.VER'
    WHEN 'AP_NOTES'            THEN 'AP.NOTAS.VER'
    WHEN 'AP_PURCHASE_ORDERS'  THEN 'AP.OC.VER'
    WHEN 'AP_RECEIPTS'         THEN 'AP.RECEPCIONES.VER'
    WHEN 'AP_REPORTS'          THEN 'AP.REPORTES.VER'
    WHEN 'AP_GOODS_RETURNS'    THEN 'AP.DEVOLUCIONES.VER'
    WHEN 'AP_INVOICES_BULK'    THEN 'AP.FACTURAS_COMPRA.CARGA_MASIVA'
    -- Contabilidad General (modulo 8)
    WHEN 'CG_COMPROBANTES'     THEN 'CG.COMPROBANTES.VER'
    WHEN 'CG_PERIODOS'         THEN 'CG.PERIODOS.VER'
    WHEN 'CG_LIBRO_DIARIO'     THEN 'CG.LIBRO_DIARIO.VER'
    WHEN 'CG_LIBRO_MAYOR'      THEN 'CG.LIBRO_MAYOR.VER'
    WHEN 'CG_BALANCE_COMPROBACION' THEN 'CG.LIBROS.VER'
    WHEN 'CG_ESTADOS_FINANCIEROS' THEN 'CG.ESTADOS_FINANCIEROS.VER'
    WHEN 'CG_CIERRE'           THEN 'CG.CIERRES.VER'
    WHEN 'CG_SERIES'           THEN 'CG.COMPROBANTES.VER'
    WHEN 'CG_REPORTES_DIAN'    THEN 'CG.REPORTES.VER'
    -- Cuentas por Cobrar (modulo 9)
    WHEN 'SALES_INVOICES'      THEN 'AR.FACTURAS_VENTA.VER'
    WHEN 'AR_PAYMENTS'         THEN 'AR.COBROS.VER'
    WHEN 'AR_ADVANCES'         THEN 'AR.ANTICIPOS.VER'
    WHEN 'AR_NOTES'            THEN 'AR.NOTAS.VER'
    WHEN 'AR_REPORTS'          THEN 'AR.FACTURAS_VENTA.VER'
    WHEN 'AR_OVERDUE'          THEN 'AR.FACTURAS_VENTA.VER'
    WHEN 'DIAN_RESOLUTIONS'    THEN 'AR.RESOLUCIONES_DIAN.VER'
    -- Integracion AAEF (modulo 10)
    WHEN 'INTEGRACION_LOTES'   THEN 'INT.LOTES.VER'
    -- Nomina (modulo 11). QA Bloque BN (2026-05-18): los codes reales sembrados
    -- por V9-Zzzzb son NOM.LIQUIDACION.* y NOM.PILA.GENERAR; antes apuntaban a
    -- NOM.RECIBOS.VER (huerfano), lo que ocultaba los 3 menus para non-admin.
    WHEN 'NOMINA_EMPLEADOS'    THEN 'NOM.EMPLEADOS.VER'
    WHEN 'NOMINA_RECIBOS'      THEN 'NOM.LIQUIDACION.VER'
    WHEN 'NOMINA_CONCEPTOS'    THEN 'NOM.CONCEPTOS.VER'
    WHEN 'NOMINA_PILA'         THEN 'NOM.PILA.GENERAR'
    WHEN 'NOMINA_PRESTACIONES' THEN 'NOM.PRESTACIONES.VER'
    WHEN 'NOMINA_RESUMEN'      THEN 'NOM.LIQUIDACION.VER'
    -- Auditoria (modulo 12)
    WHEN 'AU_LOGS'             THEN 'AU.LOG.VER'
    WHEN 'AU_DASHBOARD'        THEN 'AU.LOG.VER'
    WHEN 'AU_EXPORT'           THEN 'AU.LOG.EXPORTAR'
    WHEN 'AU_RISK_RULES'       THEN 'AU.REGLAS.VER'
    WHEN 'AU_RETENTION'        THEN 'AU.RETENCION.VER'
    WHEN 'AU_FINDINGS'         THEN 'AU.HALLAZGOS.VER'
    ELSE required_permission_code -- preservar lo que ya hubiera (idempotencia)
END
WHERE deleted_at IS NULL;

-- HU-AU-08: menu Hallazgos (faltaba en seed previo). Idempotente.
INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, required_permission_code, created_at, updated_at)
SELECT 'Hallazgos', 'ri-search-eye-line', 'hallazgos', 5, 12, 'ACTIVE', 'AU_FINDINGS', true, 'AU.HALLAZGOS.VER', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'AU_FINDINGS' AND deleted_at IS NULL);
