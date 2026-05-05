#!/usr/bin/env python
"""V3: matching con nombres reales del glosario (ya inspeccionados).

Submodulos correctos del glosario v2:
  - bancos_catalogo (no bancos)
  - cajas_de_efectivo (no cajas)
  - terceros_catalogo (no terceros)
  - cobros_y_pagos_ar (no cobros)
  - anticipos_de_clientes
  - notas_credito_debito_ap / _ar
  - ordenes_de_compra_oc
  - tasa_de_cambio (singular)
  - clasificacion_de_riesgo_niif_9 (no segmentacion_riesgo)
  - proyecciones_de_flujo_de_caja
  - parametros_del_sistema
  - cierres_contables

NO existen en glosario (catalogos del sistema sin cobertura):
  - Paises, Municipios
  - PUC, Cuentas contables (catalogo interno)
  - Permisos (atomicos en Parametrizacion)
  - Modulos (admin)
  - Menus (admin)
  - Tipos de organizacion / regimen
  - Resoluciones DIAN (estan en Cuentas por Cobrar segun glosario)
  - DIAN_XML, DIAN
  - Retenciones (asignacion sistema)
  - Account_mapping
"""
import json, re, unicodedata
from pathlib import Path

GLOSARIO = json.load(open(r'C:\Users\creds\AppData\Local\Temp\glosario.json', encoding='utf-8'))
LEGACY = [l.strip() for l in open(r'C:\Users\creds\AppData\Local\Temp\legacy_codes.txt', encoding='utf-8') if l.strip()]
OUT = r'C:\Users\creds\AppData\Local\Temp\mapping_v3.json'

def norm(s):
    if not s: return ''
    s = unicodedata.normalize('NFKD', s)
    s = ''.join(c for c in s if not unicodedata.combining(c))
    s = s.lower().replace('–','-').replace('—','-')
    s = re.sub(r'[^a-z0-9_]+', '_', s)
    return s.strip('_')

# entity (sin PERM_ ni VERBO) -> (mod_norm, sub_norm)
# CORREGIDO con nombres reales del glosario
ENTITY_TO_MODSUB = {
    # AP
    'AP_INVOICE':       ('cuentas_por_pagar', 'facturas_de_compra'),
    'AP_PAYMENT':       ('cuentas_por_pagar', 'pagos_a_proveedores'),
    'AP_ADVANCE':       ('cuentas_por_pagar', 'pagos_a_proveedores'),  # no hay anticipos en glosario AP
    'AP_NOTE':          ('cuentas_por_pagar', 'notas_credito_debito_ap'),
    'PURCHASE_ORDER':   ('cuentas_por_pagar', 'ordenes_de_compra_oc'),
    'GOODS_RECEIPT':    ('cuentas_por_pagar', 'recepciones_de_mercancia'),
    'GOODS_RETURN':     ('cuentas_por_pagar', 'devoluciones_a_proveedor'),
    'AP_REPORTS':       ('cuentas_por_pagar', 'reportes_y_exportaciones_ap'),
    'AP_REPORT':        ('cuentas_por_pagar', 'reportes_y_exportaciones_ap'),
    'INVOICE_ATTACHMENT': ('cuentas_por_pagar', 'facturas_de_compra'),  # no hay submodulo anexos
    'INVOICE_FC':       ('cuentas_por_pagar', 'facturas_de_compra'),
    'INVOICE':          ('cuentas_por_pagar', 'facturas_de_compra'),  # generico
    # AR
    'SALES_INVOICE':    ('cuentas_por_cobrar', 'facturas_de_venta'),
    'AR_PAYMENT':       ('cuentas_por_cobrar', 'cobros_y_pagos_ar'),
    'AR_ADVANCE':       ('cuentas_por_cobrar', 'anticipos_de_clientes'),
    'AR_NOTE':          ('cuentas_por_cobrar', 'notas_credito_debito_ar'),
    'AR_REPORTS':       ('cuentas_por_cobrar', 'reportes_y_exportaciones_ar'),
    'DIAN_RESOLUTION':  ('cuentas_por_cobrar', 'facturas_de_venta'),  # no hay submodulo dedicado
    'DIAN_XML':         ('cuentas_por_cobrar', 'facturas_de_venta'),
    'DIAN':             ('cuentas_por_cobrar', 'facturas_de_venta'),  # ambiguo, default a FV
    'CARTERA_VENCIDA':  ('cuentas_por_cobrar', 'reportes_y_exportaciones_ar'),
    'AR_ATTACHMENT':    ('cuentas_por_cobrar', 'facturas_de_venta'),
    # BNK
    'BANK':             ('bancos_y_cajas', 'bancos_catalogo'),
    'BANKS':            ('bancos_y_cajas', 'bancos_catalogo'),
    'BANK_ACCOUNT':     ('bancos_y_cajas', 'cuentas_bancarias'),
    'BANK_BRANCH':      ('bancos_y_cajas', 'sucursales_bancarias'),
    'BANK_CHECK':       ('bancos_y_cajas', 'cheques'),
    'CHECK':            ('bancos_y_cajas', 'cheques'),
    'CHECKBOOK':        ('bancos_y_cajas', 'chequeras'),
    'CASH':             ('bancos_y_cajas', 'cajas_de_efectivo'),
    'CASH_AUDIT':       ('bancos_y_cajas', 'arqueos_de_caja'),
    'BNK_RECONCILIATION': ('bancos_y_cajas', 'conciliacion_bancaria'),
    'BANK_RECONCILIATION':('bancos_y_cajas', 'conciliacion_bancaria'),
    'FINANCIAL_MOVEMENT': ('bancos_y_cajas', 'movimientos_bancarios'),
    'CASH_FLOW_PROJECTION': ('bancos_y_cajas', 'proyecciones_de_flujo_de_caja'),
    # CG
    'JOURNAL_ENTRY':    ('contabilidad_general', 'comprobantes_contables'),
    'VOUCHER':          ('contabilidad_general', 'comprobantes_contables'),
    'JOURNAL_ENTRY_SUPPORT': ('contabilidad_general', 'comprobantes_contables'),
    'ACCOUNTING_PERIOD':('contabilidad_general', 'periodos_contables'),
    'CLOSING':          ('contabilidad_general', 'cierres_contables'),
    'BOOK':             ('contabilidad_general', 'libro_diario'),
    'LIBRO_DIARIO':     ('contabilidad_general', 'libro_diario'),
    'LIBRO_MAYOR':      ('contabilidad_general', 'libro_mayor'),
    'BALANCE':          ('contabilidad_general', 'reportes_cg'),
    'FINANCIAL_STATEMENT':('contabilidad_general', 'estados_financieros'),
    'STATEMENT':        ('contabilidad_general', 'estados_financieros'),
    'PUC_VALIDATION':   ('contabilidad_general', 'reportes_cg'),
    'TAX_REPORT':       ('contabilidad_general', 'reportes_cg'),
    'DIAN_REPORT':      ('contabilidad_general', 'reportes_cg'),
    'ACCOUNTING':       ('contabilidad_general', 'comprobantes_contables'),
    # ACT
    'ASSET':            ('activos_fijos', 'activos'),
    'ASSET_DISPOSAL':   ('activos_fijos', 'activos'),
    'DEPRECIATION':     ('activos_fijos', 'activos'),
    'NIIF_ALERT':       ('activos_fijos', 'activos'),
    'NIIF':             ('activos_fijos', 'activos'),
    'ASSET_REPORT':     ('activos_fijos', 'activos'),
    'ASSET_ANNUAL_REVIEW': ('activos_fijos', 'activos'),
    # TER
    'THIRD_PARTY':      ('terceros', 'terceros_catalogo'),
    'THIRD_PARTIES':    ('terceros', 'terceros_catalogo'),
    'COMMERCIAL_DATA':  ('terceros', 'datos_comerciales_de_terceros'),
    'ECL_SEGMENT':      ('terceros', 'clasificacion_de_riesgo_niif_9'),
    'ECL_SEGMENTATION': ('terceros', 'clasificacion_de_riesgo_niif_9'),
    'THIRD_PARTY_BANK_ACCOUNT': ('terceros', 'cuentas_bancarias_de_terceros'),
    'THIRD_PARTY_ATTACHMENT':   ('terceros', 'terceros_catalogo'),
    # NOM
    'EMPLOYEE':         ('nomina', 'liquidacion_de_nomina'),
    'PAYROLL':          ('nomina', 'liquidacion_de_nomina'),
    'PAYROLL_RECEIPT':  ('nomina', 'comprobantes_de_pago'),
    'PAYROLL_CONCEPT':  ('nomina', 'liquidacion_de_nomina'),
    'PILA':             ('nomina', 'pila'),
    'BENEFIT_LIQUIDATION': ('nomina', 'liquidacion_de_nomina'),
    # AU
    'AUDIT':            ('auditoria', 'log_de_auditoria'),
    'AUDIT_LOG':        ('auditoria', 'log_de_auditoria'),
    'AUDITORIA':        ('auditoria', 'log_de_auditoria'),
    'AUDIT_RISK_RULE':  ('auditoria', 'log_de_auditoria'),
    'RETENTION_POLICY': ('auditoria', 'log_de_auditoria'),
    'AUDIT_FINDING':    ('auditoria', 'log_de_auditoria'),
    # INT
    'AAEF_BATCH':       ('integracion_aaef', 'lotes_aaef'),
    'BATCH':            ('integracion_aaef', 'lotes_aaef'),
    'INTEGRATION_TRANSFER': ('integracion_aaef', 'lotes_aaef'),
    'TRANSFER':         ('integracion_aaef', 'lotes_aaef'),
    'INTEGRATION_HEALTH': ('integracion_aaef', 'lotes_aaef'),
    'INTEGRATION':      ('integracion_aaef', 'lotes_aaef'),
    # CFG
    'COST_CENTER':      ('listas_contables', 'centros_de_costo'),
    'CURRENCY_TYPE':    ('listas_contables', 'monedas'),
    'CURRENCY':         ('listas_contables', 'monedas'),
    'EXCHANGE_RATE':    ('listas_contables', 'tasa_de_cambio'),
    'EXCHANGE_RATES':   ('listas_contables', 'tasa_de_cambio'),
    'RULER_TAX':        ('listas_contables', 'reglas_tributarias'),
    'TAX_RULE':         ('listas_contables', 'reglas_tributarias'),
    'DEPRECIATION_RULE':('listas_contables', 'reglas_de_depreciacion'),
    'DEPRETATION_RULE': ('listas_contables', 'reglas_de_depreciacion'),
    'PAYMENT_FORM':     ('listas_contables', 'formas_de_pago'),
    'PAYMENT_TERM':     ('listas_contables', 'plazos_de_pago'),
    # PAR
    'USER':             ('parametrizacion', 'usuarios'),
    'ROLE':             ('parametrizacion', 'roles'),
    'MENU':             ('parametrizacion', 'menus'),
    'MENUS':            ('parametrizacion', 'menus'),
    'MODULE':           ('parametrizacion', 'modulos'),
    'MODULES':          ('parametrizacion', 'modulos'),
    'MODULES_MENU':     ('parametrizacion', 'menus'),
    'PARAMETER':        ('parametrizacion', 'parametros_del_sistema'),
    'REPORT_TYPE':      ('parametrizacion', 'tipos_de_reporte'),
    'REPORT_TYPES':     ('parametrizacion', 'tipos_de_reporte'),
    'REPORT_TEMPLATE':  ('parametrizacion', 'plantillas_de_reporte'),
    'REPORT_TEMPLATES': ('parametrizacion', 'plantillas_de_reporte'),
    'REPORTS_TYPES':    ('parametrizacion', 'tipos_de_reporte'),
    'REPORTS_TEMPLATES':('parametrizacion', 'plantillas_de_reporte'),
    'ROLES':            ('parametrizacion', 'roles'),
    'USERS':            ('parametrizacion', 'usuarios'),
    'COST_CENTERS':     ('listas_contables', 'centros_de_costo'),
    # PLAT
    'COMPANY':          ('plataforma', 'empresas'),
    'PLATFORM_USER':    ('plataforma', 'usuarios_de_plataforma'),
    'PLATFORM_DASHBOARD':('plataforma', 'dashboard_de_plataforma'),
    'JWT_AUDIT':        ('plataforma', 'audit_log_de_plataforma'),
    'JWT_CONFIG':       ('plataforma', 'api_key_agrofusion'),
}

# Entidades NO cubiertas por glosario (catalogos del sistema). Mapea a "ROLE_ADMIN_ONLY"
# para reemplazar @PreAuthorize por hasAnyRole('ADMIN_EMPRESA','PLATFORM_ADMIN').
NOT_IN_GLOSSARY = {
    'COUNTRY', 'COUNTRIES',
    'MUNICIPALITY', 'MUNICIPALITIES',
    'PUC', 'CHART_OF_ACCOUNT',
    'ACCOUNTING_ACCOUNT',
    'PERMISSION', 'PERMISSIONS',
    'MENU_PERMISSION', 'MENU_PERMISSIONS',
    'WITHHOLDING', 'WITHHOLDINGS',
    'SYSTEM_WITHHOLDING_ASSIGNMENT',
    'ACCOUNT_MAPPING',
    'TYPES_ORGANIZATIONS', 'TYPE_ORGANIZATION',
    'TYPES_REGIMES', 'TYPE_REGIMEN',
}

VERB_TO_ACTION_TOKENS = {
    'CREATE': ['crear','registrar','generar','iniciar','agregar'],
    'STORE':  ['crear','registrar'], 'NEW': ['crear'],
    'BULK_STORE': ['importar_masivo','bulk','masivo','carga_masiva'],
    'BULK':   ['importar_masivo','masivo'],
    'IMPORT': ['importar_masivo','importar'],
    'VIEW':   ['ver','consultar','listar','visualizar'],
    'READ':   ['ver','consultar','listar'],
    'LIST':   ['ver','listar'], 'GET': ['ver','consultar'],
    'SEARCH': ['ver','buscar','consultar'],
    'UPDATE': ['editar','actualizar','modificar'],
    'EDIT':   ['editar','modificar'], 'MODIFY': ['editar','modificar'],
    'DELETE': ['eliminar','borrar','dar_de_baja'],
    'REMOVE': ['eliminar','quitar'],
    'APPROVE':['aprobar','validar'], 'REJECT': ['rechazar'],
    'CANCEL': ['anular'], 'VOID': ['anular'],
    'REVERSE':['reversar','reverso'],
    'EXPORT': ['exportar','generar','descargar','pdf','excel','csv','xml'],
    'DOWNLOAD':['descargar','exportar'],
    'PRINT':  ['imprimir','exportar'],
    'CALCULATE':['ejecutar','calcular','recalcular','depreciar'],
    'ADJUST': ['ajustar','manual'],
    'CHANGE_STATUS':['cambiar_estado','cambiar','activar','desactivar'],
    'CHANGE': ['cambiar'],
    'ASSIGN': ['asignar','vincular'],
    'CLOSE':  ['cerrar'], 'OPEN': ['abrir'],
    'LOCK':   ['bloquear'], 'UNLOCK': ['desbloquear','liberar'],
    'REOPEN': ['reabrir'],
    'SUBMIT': ['enviar','dian'],
    'GENERATE':['generar','exportar'],
    'POST':   ['contabilizar','aprobar'],
    'SETTLE': ['liquidar','conciliar','cerrar'],
    'PAY':    ['crear','registrar'],
    'LIQUIDATE':['liquidar'],
    'RECONCILE':['conciliar'], 'MATCH': ['conciliar','vincular'],
    'UNMATCH':['desvincular'], 'LINK': ['vincular'], 'UNLINK': ['desvincular'],
    'ACTIVATE':['cambiar_estado','activar'],
    'DEACTIVATE':['cambiar_estado','desactivar','dar_de_baja'],
    'INACTIVATE':['cambiar_estado','desactivar','dar_de_baja'],
    'RUN':    ['ejecutar'], 'EXECUTE': ['ejecutar'],
    'PURGE':  ['purgar','eliminar'],
    'RETRY':  ['reintentar'],
    'NOTIFY': ['crear','notificar'], 'REGISTER': ['crear','registrar'],
    'AUDIT':  ['ver','consultar'],
    'CONSOLIDATE':['ejecutar','consolidar'],
    'COMPARE':['ver','comparar'],
    'DAR_DE_BAJA':['dar_de_baja','eliminar'], 'DISPOSAL':['dar_de_baja'],
    'TRANSFER':['transferir','vincular'],
    'REVALUATE':['revaluar'], 'CORRECT': ['editar','corregir'],
    'NIIF_VERIFY':['verificar','niif'],
    'VERIFY': ['verificar','ver'], 'VALIDATE': ['validar','ver'],
    'RELEASE':['liberar'], 'HOLD': ['legal_hold','retener'],
    'CONFIGURE':['configurar','editar'],
    'EXPORT_TO_DIAN':['exportar_dian','dian','enviar_dian'],
    'MANAGE': ['editar','gestionar','asignar'],
    'REPORT': ['crear','registrar','reportar'],  # REPORT_LOST = registrar perdida
    'REPORT_LOST': ['extravio','reportar'],
}

ALL_VERBS = list(VERB_TO_ACTION_TOKENS.keys())
COMPOUND = sorted([v for v in ALL_VERBS if '_' in v], key=len, reverse=True)
SIMPLE   = sorted([v for v in ALL_VERBS if '_' not in v], key=len, reverse=True)

def split_legacy(code):
    body = code.replace('PERM_', '', 1)
    for v in COMPOUND:
        if body == v: return v, ''
        if body.startswith(v+'_'): return v, body[len(v)+1:]
    for v in SIMPLE:
        if body == v: return v, ''
        if body.startswith(v+'_'): return v, body[len(v)+1:]
    return None, body

GLOS_BY_MODSUB = {}
for p in GLOSARIO:
    key = (norm(p['module']), norm(p['submodule']))
    GLOS_BY_MODSUB.setdefault(key, []).append(p)

matched = {}
unmatched = []
admin_only = []  # entidades NO cubiertas por glosario

def match_legacy(code):
    verb, entity = split_legacy(code)
    if verb is None or not entity:
        return None, None, f'verb_not_recognized'
    # Check NOT_IN_GLOSSARY first (catalogos sistema)
    for ent_key in sorted(NOT_IN_GLOSSARY, key=len, reverse=True):
        if entity == ent_key or entity.startswith(ent_key+'_') or entity.startswith(ent_key):
            return None, 'admin_only', f'catalog_not_in_glossary: {entity}'
    target = None
    for ent_key in sorted(ENTITY_TO_MODSUB.keys(), key=len, reverse=True):
        if entity == ent_key or entity.startswith(ent_key+'_'):
            target = ENTITY_TO_MODSUB[ent_key]
            break
    if not target:
        return None, None, f'entity_not_mapped: {entity}'
    candidates = GLOS_BY_MODSUB.get(target, [])
    if not candidates:
        return None, None, f'modsub_not_in_glossary: {target}'
    verb_tokens = VERB_TO_ACTION_TOKENS.get(verb, [])
    best = None
    best_score = -1
    for p in candidates:
        action_n = norm(p['action'])
        code_n = norm(p['code'])
        score = 0
        for tok in verb_tokens:
            if tok == action_n: score += 5
            elif tok in action_n: score += 3
            elif tok in code_n: score += 1
        if score > best_score:
            best = p
            best_score = score
    if best_score == 0:
        # fallback: si VERB es de lectura -> usar VER del modsub
        if verb in ('VIEW','READ','LIST','GET','SEARCH'):
            for p in candidates:
                if 'ver' in norm(p['action']):
                    return p['code'], None, 'fallback_VER'
        return best['code'], None, f'low_score (verb={verb})'
    return best['code'], None, None

for code in LEGACY:
    new_code, kind, note = match_legacy(code)
    if kind == 'admin_only':
        admin_only.append({'code': code, 'reason': note})
    elif new_code:
        matched[code] = {'new': new_code, 'note': note}
    else:
        unmatched.append({'code': code, 'reason': note})

print(f"Total: {len(LEGACY)}")
print(f"  matched (glosario): {len(matched)}")
print(f"  admin_only (catalogos): {len(admin_only)}")
print(f"  unmatched: {len(unmatched)}")
json.dump({'matched': matched, 'admin_only': admin_only, 'unmatched': unmatched},
          open(OUT,'w',encoding='utf-8'), indent=2, ensure_ascii=False)
print(f"\nMapping: {OUT}")

print(f"\n=== UNMATCHED ({len(unmatched)}) ===")
for u in unmatched[:15]: print(f"  {u}")
print(f"\n=== ADMIN_ONLY ({len(admin_only)}) ===")
for u in admin_only: print(f"  {u['code']} -> {u['reason']}")
