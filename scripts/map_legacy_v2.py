#!/usr/bin/env python
"""V2: matching tolerante a tildes/em-dash/espacios.

Para cada legacy code:
1. Identifica VERB + ENTITY.
2. Mapea ENTITY a (modulo, submodulo) glosario.
3. Filtra el glosario por ese (modulo, submodulo) -> obtiene N candidatos.
4. Busca el candidato cuya 'action' coincida con el VERB (heuristica).
5. Si no hay match, fallback al permiso .VER del mismo (mod, sub) si VERB es READ-like.
"""
import json, re, unicodedata
from pathlib import Path

GLOSARIO = json.load(open(r'C:\Users\creds\AppData\Local\Temp\glosario.json', encoding='utf-8'))
LEGACY = [l.strip() for l in open(r'C:\Users\creds\AppData\Local\Temp\legacy_codes.txt', encoding='utf-8') if l.strip()]
OUT = r'C:\Users\creds\AppData\Local\Temp\mapping_v2.json'

def norm(s):
    if not s: return ''
    s = unicodedata.normalize('NFKD', s)
    s = ''.join(c for c in s if not unicodedata.combining(c))
    s = s.lower().replace('–','-').replace('—','-')
    s = re.sub(r'[^a-z0-9_]+', '_', s)
    return s.strip('_')

# entity (sin PERM_ ni VERBO) -> (modulo, submodulo) en string normalizado
ENTITY_TO_MODSUB = {
    'AP_INVOICE': ('cuentas_por_pagar', 'facturas_de_compra'),
    'AP_PAYMENT': ('cuentas_por_pagar', 'pagos_a_proveedores'),
    'AP_ADVANCE': ('cuentas_por_pagar', 'anticipos_a_proveedores'),
    'AP_NOTE':    ('cuentas_por_pagar', 'notas_credito_y_debito'),
    'PURCHASE_ORDER': ('cuentas_por_pagar', 'ordenes_de_compra'),
    'GOODS_RECEIPT':  ('cuentas_por_pagar', 'recepciones_de_mercancia'),
    'GOODS_RETURN':   ('cuentas_por_pagar', 'devoluciones_de_mercancia'),
    'AP_REPORTS':     ('cuentas_por_pagar', 'reportes_ap'),
    'INVOICE_ATTACHMENT': ('cuentas_por_pagar', 'anexos_factura'),
    'INVOICE_FC':     ('cuentas_por_pagar', 'facturas_de_compra'),
    'SALES_INVOICE':  ('cuentas_por_cobrar', 'facturas_de_venta'),
    'AR_PAYMENT':     ('cuentas_por_cobrar', 'cobros'),
    'AR_ADVANCE':     ('cuentas_por_cobrar', 'anticipos_clientes'),
    'AR_NOTE':        ('cuentas_por_cobrar', 'notas_credito_y_debito_venta'),
    'AR_REPORTS':     ('cuentas_por_cobrar', 'reportes_ar'),
    'DIAN_RESOLUTION': ('cuentas_por_cobrar', 'resoluciones_dian'),
    'DIAN_XML':        ('cuentas_por_cobrar', 'facturas_de_venta'),
    'CARTERA_VENCIDA': ('cuentas_por_cobrar', 'cartera_vencida'),
    'AR_ATTACHMENT':   ('cuentas_por_cobrar', 'anexos_factura_venta'),
    'BANK':         ('bancos_y_cajas', 'bancos'),
    'BANKS':        ('bancos_y_cajas', 'bancos'),
    'BANK_ACCOUNT': ('bancos_y_cajas', 'cuentas_bancarias'),
    'BANK_BRANCH':  ('bancos_y_cajas', 'sucursales_bancarias'),
    'BANK_CHECK':   ('bancos_y_cajas', 'cheques'),
    'CHECK':        ('bancos_y_cajas', 'cheques'),
    'CHECKBOOK':    ('bancos_y_cajas', 'chequeras'),
    'CASH':         ('bancos_y_cajas', 'cajas'),
    'CASH_AUDIT':   ('bancos_y_cajas', 'arqueos_de_caja'),
    'BNK_RECONCILIATION':  ('bancos_y_cajas', 'conciliacion_bancaria'),
    'BANK_RECONCILIATION': ('bancos_y_cajas', 'conciliacion_bancaria'),
    'FINANCIAL_MOVEMENT':  ('bancos_y_cajas', 'movimientos_financieros'),
    'CASH_FLOW_PROJECTION': ('bancos_y_cajas', 'proyecciones_de_flujo'),
    'JOURNAL_ENTRY':   ('contabilidad_general', 'comprobantes_contables'),
    'VOUCHER':         ('contabilidad_general', 'comprobantes_contables'),
    'JOURNAL_ENTRY_SUPPORT': ('contabilidad_general', 'soportes_comprobante'),
    'ACCOUNTING_PERIOD': ('contabilidad_general', 'periodos_contables'),
    'CLOSING':         ('contabilidad_general', 'cierres'),
    'BOOK':            ('contabilidad_general', 'libro_diario'),
    'LIBRO_DIARIO':    ('contabilidad_general', 'libro_diario'),
    'LIBRO_MAYOR':     ('contabilidad_general', 'libro_mayor'),
    'BALANCE':         ('contabilidad_general', 'balance_de_comprobacion'),
    'FINANCIAL_STATEMENT':('contabilidad_general', 'estados_financieros'),
    'STATEMENT':       ('contabilidad_general', 'estados_financieros'),
    'PUC_VALIDATION':  ('contabilidad_general', 'validacion_puc'),
    'TAX_REPORT':      ('contabilidad_general', 'reportes_tributarios'),
    'DIAN_REPORT':     ('contabilidad_general', 'reportes_dian_exogena'),
    'ASSET':           ('activos_fijos', 'activos'),
    'ASSET_DISPOSAL':  ('activos_fijos', 'activos'),
    'DEPRECIATION':    ('activos_fijos', 'depreciacion'),
    'NIIF_ALERT':      ('activos_fijos', 'verificacion_niif'),
    'NIIF':            ('activos_fijos', 'verificacion_niif'),
    'ASSET_REPORT':    ('activos_fijos', 'reportes_de_activos'),
    'ASSET_ANNUAL_REVIEW': ('activos_fijos', 'revision_anual_niif'),
    'THIRD_PARTY':     ('terceros', 'terceros'),
    'COMMERCIAL_DATA': ('terceros', 'datos_comerciales'),
    'ECL_SEGMENT':     ('terceros', 'segmentacion_riesgo'),
    'ECL_SEGMENTATION':('terceros', 'segmentacion_riesgo'),
    'THIRD_PARTY_BANK_ACCOUNT': ('terceros', 'cuentas_bancarias_del_tercero'),
    'THIRD_PARTY_ATTACHMENT':   ('terceros', 'adjuntos_tercero'),
    'ACCOUNTING_ACCOUNT': ('listas_contables', 'cuentas_contables'),
    'COST_CENTER':        ('listas_contables', 'centros_de_costo'),
    'CHART_OF_ACCOUNT':   ('listas_contables', 'puc'),
    'PUC':                ('listas_contables', 'puc'),
    'CURRENCY_TYPE':      ('listas_contables', 'monedas'),
    'CURRENCY':           ('listas_contables', 'monedas'),
    'EXCHANGE_RATE':      ('listas_contables', 'tasas_de_cambio'),
    'EXCHANGE_RATES':     ('listas_contables', 'tasas_de_cambio'),
    'RULER_TAX':          ('listas_contables', 'reglas_tributarias'),
    'TAX_RULE':           ('listas_contables', 'reglas_tributarias'),
    'DEPRECIATION_RULE':  ('listas_contables', 'reglas_de_depreciacion'),
    'DEPRETATION_RULE':   ('listas_contables', 'reglas_de_depreciacion'),
    'PAYMENT_FORM':       ('listas_contables', 'formas_de_pago'),
    'WITHHOLDING':        ('listas_contables', 'retenciones'),
    'SYSTEM_WITHHOLDING_ASSIGNMENT': ('listas_contables', 'asignacion_retenciones_sistema'),
    'ACCOUNT_MAPPING':    ('listas_contables', 'mapeo_cuentas'),
    'EMPLOYEE':           ('nomina', 'empleados'),
    'PAYROLL':            ('nomina', 'liquidacion_de_nomina'),
    'PAYROLL_RECEIPT':    ('nomina', 'comprobantes_de_nomina'),
    'PAYROLL_CONCEPT':    ('nomina', 'conceptos_de_nomina'),
    'PILA':               ('nomina', 'reporte_pila'),
    'BENEFIT_LIQUIDATION':('nomina', 'liquidacion_de_prestaciones'),
    'AUDIT':         ('auditoria', 'log_de_auditoria'),
    'AUDIT_LOG':     ('auditoria', 'log_de_auditoria'),
    'AUDITORIA':     ('auditoria', 'log_de_auditoria'),
    'AUDIT_RISK_RULE':   ('auditoria', 'reglas_de_riesgo'),
    'RETENTION_POLICY':  ('auditoria', 'politicas_de_retencion'),
    'AUDIT_FINDING':     ('auditoria', 'hallazgos'),
    'AAEF_BATCH':       ('integracion_aaef', 'lotes_aaef'),
    'BATCH':            ('integracion_aaef', 'lotes_aaef'),
    'INTEGRATION_TRANSFER': ('integracion_aaef', 'transferencias'),
    'TRANSFER':             ('integracion_aaef', 'transferencias'),
    'INTEGRATION_HEALTH':   ('integracion_aaef', 'salud_integracion'),
    'INTEGRATION':          ('integracion_aaef', 'lotes_aaef'),
    'USER':           ('parametrizacion', 'usuarios'),
    'ROLE':           ('parametrizacion', 'roles'),
    'PERMISSION':     ('parametrizacion', 'permisos'),
    'PERMISSIONS':    ('parametrizacion', 'permisos'),
    'MENU':           ('parametrizacion', 'menus'),
    'MENUS':          ('parametrizacion', 'menus'),
    'MODULE':         ('parametrizacion', 'modulos'),
    'MODULES':        ('parametrizacion', 'modulos'),
    'MODULES_MENU':   ('parametrizacion', 'menus'),
    'PARAMETER':      ('parametrizacion', 'parametros'),
    'REPORT_TYPE':    ('parametrizacion', 'tipos_de_reporte'),
    'REPORT_TEMPLATE':('parametrizacion', 'plantillas_de_reporte'),
    'REPORTS_TYPES':  ('parametrizacion', 'tipos_de_reporte'),
    'REPORTS_TEMPLATES':('parametrizacion', 'plantillas_de_reporte'),
    'COUNTRY':       ('parametrizacion', 'paises'),
    'MUNICIPALITY':  ('parametrizacion', 'municipios'),
    'MENU_PERMISSION':  ('parametrizacion', 'permisos_de_menu'),
    'MENU_PERMISSIONS': ('parametrizacion', 'permisos_de_menu'),
    'COMPANY':        ('plataforma', 'empresas'),
    'PLATFORM_USER':  ('plataforma', 'usuarios_plataforma'),
    'PLATFORM_DASHBOARD': ('plataforma', 'dashboard_plataforma'),
    'JWT_AUDIT':      ('plataforma', 'auditoria_jwt'),
    'JWT_CONFIG':     ('plataforma', 'configuracion_jwt'),
    'ACCOUNTING':     ('contabilidad_general', 'comprobantes_contables'),
    'PUC_VALIDATION': ('contabilidad_general', 'validacion_puc'),
}

# VERB legacy -> set de tokens que pueden estar en la accion del glosario
VERB_TO_ACTION_TOKENS = {
    'CREATE': ['crear','registrar','generar','iniciar','agregar'],
    'STORE':  ['crear','registrar'],
    'NEW':    ['crear'],
    'BULK_STORE': ['importar_masivo','bulk','masivo'],
    'BULK':   ['importar_masivo','masivo'],
    'IMPORT': ['importar_masivo','importar'],
    'VIEW':   ['ver','consultar','listar','visualizar'],
    'READ':   ['ver','consultar','listar'],
    'LIST':   ['ver','listar'],
    'GET':    ['ver','consultar'],
    'SEARCH': ['ver','buscar','consultar'],
    'UPDATE': ['editar','actualizar','modificar'],
    'EDIT':   ['editar','modificar'],
    'MODIFY': ['editar','modificar'],
    'DELETE': ['eliminar','borrar','dar_de_baja'],
    'REMOVE': ['eliminar','quitar'],
    'APPROVE':['aprobar','validar'],
    'REJECT': ['rechazar'],
    'CANCEL': ['anular'],
    'VOID':   ['anular'],
    'REVERSE':['reversar','reverso'],
    'EXPORT': ['exportar','generar_pdf','generar_excel','descargar','pdf','excel','csv','xml'],
    'DOWNLOAD':['descargar','exportar'],
    'PRINT':  ['imprimir','exportar'],
    'CALCULATE':['ejecutar','calcular','recalcular'],
    'ADJUST': ['ajustar'],
    'CHANGE_STATUS':['cambiar_estado','cambiar'],
    'CHANGE': ['cambiar'],
    'ASSIGN': ['asignar'],
    'CLOSE':  ['cerrar'],
    'OPEN':   ['abrir'],
    'LOCK':   ['bloquear'],
    'UNLOCK': ['desbloquear','liberar'],
    'REOPEN': ['reabrir'],
    'SUBMIT': ['enviar','dian','enviar_dian'],
    'GENERATE':['generar','exportar'],
    'POST':   ['contabilizar','aprobar'],
    'SETTLE': ['liquidar','conciliar','cerrar'],
    'PAY':    ['crear','registrar'],
    'LIQUIDATE':['liquidar'],
    'RECONCILE':['conciliar'],
    'MATCH':  ['conciliar','vincular'],
    'UNMATCH':['conciliar','desvincular'],
    'LINK':   ['vincular','asignar'],
    'UNLINK': ['desvincular'],
    'ACTIVATE':['cambiar_estado','activar'],
    'DEACTIVATE':['cambiar_estado','desactivar','dar_de_baja'],
    'INACTIVATE':['cambiar_estado','desactivar'],
    'RUN':    ['ejecutar'],
    'EXECUTE':['ejecutar'],
    'PURGE':  ['purgar','eliminar'],
    'RETRY':  ['reintentar'],
    'NOTIFY': ['crear','notificar'],
    'REGISTER':['crear','registrar'],
    'AUDIT':  ['ver','consultar'],
    'CONSOLIDATE':['ejecutar','consolidar'],
    'COMPARE':['ver','comparar'],
    'DAR_DE_BAJA':['dar_de_baja','eliminar'],
    'DISPOSAL':['dar_de_baja'],
    'TRANSFER':['transferir','vincular'],
    'REVALUATE':['revaluar'],
    'CORRECT':['editar','corregir'],
    'NIIF_VERIFY':['verificar','niif'],
    'VERIFY': ['verificar','ver'],
    'VALIDATE':['validar','ver'],
    'RELEASE':['liberar'],
    'HOLD':   ['legal_hold','retener'],
    'CONFIGURE':['configurar','editar'],
    'EXPORT_TO_DIAN':['exportar_dian','dian','enviar_dian'],
}

# Compound y simple verbs ordenados por longitud descendente
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

# Indice glosario: por (mod_n, sub_n) -> list of permisos
GLOS_BY_MODSUB = {}
for p in GLOSARIO:
    key = (norm(p['module']), norm(p['submodule']))
    GLOS_BY_MODSUB.setdefault(key, []).append(p)

# Indice glosario directo por code
GLOS_BY_CODE = {p['code']: p for p in GLOSARIO}

# === Procesar ===
matched = {}
unmatched = []

def match_legacy(code):
    verb, entity = split_legacy(code)
    if verb is None or not entity:
        return None, f'verb_not_recognized: {code}'
    # Buscar mapeo entity -> (mod, sub). Probar prefijos largos primero.
    target = None
    matched_entity = None
    for ent_key in sorted(ENTITY_TO_MODSUB.keys(), key=len, reverse=True):
        if entity == ent_key or entity.startswith(ent_key):
            target = ENTITY_TO_MODSUB[ent_key]
            matched_entity = ent_key
            break
    if not target:
        return None, f'entity_not_mapped: {entity}'
    candidates = GLOS_BY_MODSUB.get(target, [])
    if not candidates:
        return None, f'modsub_not_in_glossary: {target}'
    # Tokens del verb
    verb_tokens = VERB_TO_ACTION_TOKENS.get(verb, [])
    # Score cada candidato por overlap con verb tokens en el code o accion glosario
    best = None
    best_score = -1
    for p in candidates:
        action_n = norm(p['action'])
        code_n = norm(p['code'])
        score = 0
        for tok in verb_tokens:
            if tok in action_n or tok in code_n:
                score += 2
        # Bonus si el verb es CREATE y action_n contiene 'crear'
        if best is None or score > best_score:
            best = p
            best_score = score
    if best is None:
        return None, f'no_candidate'
    # Si el score es 0, es match defaultivo (no hay accion equivalente). Acepto pero marco
    if best_score == 0:
        # Caso comun: el VERBO no produce un permiso atomico distinto en el glosario.
        # Fallback: usar permiso .VER del mismo modsub si existe.
        for p in candidates:
            if 'ver' in norm(p['action']):
                return p['code'], f'fallback_to_VER (verb={verb} sin accion equivalente)'
        return best['code'], f'low_score (verb={verb})'
    return best['code'], None

for code in LEGACY:
    new_code, note = match_legacy(code)
    if new_code:
        matched[code] = {'new': new_code, 'note': note}
    else:
        unmatched.append({'code': code, 'reason': note})

print(f"Total: {len(LEGACY)}")
print(f"  matched: {len(matched)}")
print(f"  unmatched: {len(unmatched)}")
json.dump({'matched': matched, 'unmatched': unmatched}, open(OUT,'w',encoding='utf-8'), indent=2, ensure_ascii=False)
print(f"\nMapping: {OUT}")
print(f"\n=== UNMATCHED ({len(unmatched)}) ===")
for u in unmatched: print(f"  {u}")
