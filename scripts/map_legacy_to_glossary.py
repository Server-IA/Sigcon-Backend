#!/usr/bin/env python
"""Construye un mapping de codes legacy PERM_<VERBO>_<ENTIDAD> a codes del
glosario v2 PERM_<MODULO>.<SUBMODULO>.<ACCION>.

Estrategia:
  1. Para cada code legacy, separa VERBO + ENTIDAD.
  2. Mapea VERBO -> accion glosario (CREATE->CREAR, VIEW/READ->VER, etc).
  3. Mapea ENTIDAD -> tupla (modulo glosario, submodulo glosario) usando
     diccionario manual curado. Para entidades no mapeadas, deja en "ambiguous".
  4. Busca en el glosario un permiso cuyo MODULO + SUBMODULO + ACCION coincida.
  5. Reporta:
     - matched: legacy -> nuevo
     - unmatched: legacy sin equivalente en glosario
     - ambiguous: legacy con varios candidatos posibles
"""
import json, re, sys
from pathlib import Path

GLOSARIO_JSON = Path(r'C:\Users\creds\AppData\Local\Temp\glosario.json')
LEGACY_FILE = Path(r'C:\Users\creds\AppData\Local\Temp\legacy_codes.txt')
OUTPUT_MAPPING = Path(r'C:\Users\creds\AppData\Local\Temp\mapping_legacy_to_glossary.json')

with GLOSARIO_JSON.open(encoding='utf-8') as f:
    glosario = json.load(f)

with LEGACY_FILE.open(encoding='utf-8') as f:
    legacy_codes = [l.strip() for l in f if l.strip()]

# === Mapeo VERBO legacy -> Accion glosario ===
VERB_TO_ACTION = {
    'CREATE': 'CREAR',
    'STORE': 'CREAR',
    'NEW': 'CREAR',
    'BULK_STORE': 'IMPORTAR_MASIVO',
    'BULK': 'IMPORTAR_MASIVO',
    'IMPORT': 'IMPORTAR_MASIVO',
    'VIEW': 'VER',
    'READ': 'VER',
    'LIST': 'VER',
    'GET': 'VER',
    'SEARCH': 'VER',
    'UPDATE': 'EDITAR',
    'EDIT': 'EDITAR',
    'MODIFY': 'EDITAR',
    'DELETE': 'ELIMINAR',
    'REMOVE': 'ELIMINAR',
    'APPROVE': 'APROBAR',
    'REJECT': 'RECHAZAR',
    'CANCEL': 'ANULAR',
    'VOID': 'ANULAR',
    'REVERSE': 'REVERSAR',
    'EXPORT': 'EXPORTAR',
    'DOWNLOAD': 'EXPORTAR',
    'PRINT': 'EXPORTAR',
    'CALCULATE': 'EJECUTAR_DEPRECIACION',  # contexto especifico
    'ADJUST': 'AJUSTAR_MANUAL',
    'CHANGE_STATUS': 'CAMBIAR_ESTADO',
    'ASSIGN': 'ASIGNAR',
    'CLOSE': 'CERRAR',
    'OPEN': 'ABRIR',
    'LOCK': 'BLOQUEAR',
    'REOPEN': 'REABRIR',
    'SUBMIT': 'ENVIAR_DIAN',
    'GENERATE': 'GENERAR',
    'POST': 'CONTABILIZAR',
    'SETTLE': 'LIQUIDAR',
    'PAY': 'CREAR',  # CREATE_PAYMENT genérico
    'LIQUIDATE': 'LIQUIDAR',
    'RECONCILE': 'CONCILIAR',
    'MATCH': 'CONCILIAR',
    'UNMATCH': 'CONCILIAR',
    'LINK': 'VINCULAR',
    'UNLINK': 'VINCULAR',
    'ACTIVATE': 'CAMBIAR_ESTADO',
    'DEACTIVATE': 'CAMBIAR_ESTADO',
    'RUN': 'EJECUTAR',
    'EXECUTE': 'EJECUTAR',
    'PURGE': 'PURGAR',
    'RETRY': 'REINTENTAR_DOCUMENTO',
    'NOTIFY': 'CREAR',
    'REGISTER': 'CREAR',
    'AUDIT': 'VER',
    'CONSOLIDATE': 'EJECUTAR',
    'COMPARE': 'VER',
    'INACTIVATE': 'CAMBIAR_ESTADO',
    'DAR_DE_BAJA': 'DAR_DE_BAJA',
    'DISPOSAL': 'DAR_DE_BAJA',
    'TRANSFER': 'TRANSFERIR',
    'REVALUATE': 'REVALUAR',
    'CORRECT': 'EDITAR',
    'NIIF_VERIFY': 'VERIFICAR_NIIF',
    'VERIFY': 'VER',
    'VALIDATE': 'VER',
    'RELEASE': 'LIBERAR_LEGAL_HOLD',
    'HOLD': 'LEGAL_HOLD',
    'CONFIGURE': 'CONFIGURAR',
    'EXPORT_TO_DIAN': 'EXPORTAR_DIAN',
}

# === Mapeo ENTIDAD legacy -> (modulo, submodulo) glosario ===
# El submodulo debe matchear EXACTAMENTE el campo "submodule" del glosario
ENTITY_TO_MODSUB = {
    # AP
    'AP_INVOICE': ('Cuentas por Pagar', 'Facturas de compra'),
    'AP_PAYMENT': ('Cuentas por Pagar', 'Pagos a proveedores'),
    'AP_ADVANCE': ('Cuentas por Pagar', 'Anticipos a proveedores'),
    'AP_NOTE': ('Cuentas por Pagar', 'Notas credito y debito'),
    'PURCHASE_ORDER': ('Cuentas por Pagar', 'Ordenes de compra'),
    'GOODS_RECEIPT': ('Cuentas por Pagar', 'Recepciones de mercancia'),
    'GOODS_RETURN': ('Cuentas por Pagar', 'Devoluciones de mercancia'),
    'AP_REPORTS': ('Cuentas por Pagar', 'Reportes AP'),
    'INVOICE_ATTACHMENT': ('Cuentas por Pagar', 'Anexos factura'),
    # AR
    'SALES_INVOICE': ('Cuentas por Cobrar', 'Facturas de venta'),
    'AR_PAYMENT': ('Cuentas por Cobrar', 'Cobros'),
    'AR_ADVANCE': ('Cuentas por Cobrar', 'Anticipos clientes'),
    'AR_NOTE': ('Cuentas por Cobrar', 'Notas credito y debito venta'),
    'AR_REPORTS': ('Cuentas por Cobrar', 'Reportes AR'),
    'DIAN_RESOLUTION': ('Cuentas por Cobrar', 'Resoluciones DIAN'),
    'CARTERA_VENCIDA': ('Cuentas por Cobrar', 'Cartera vencida'),
    'AR_ATTACHMENT': ('Cuentas por Cobrar', 'Anexos factura venta'),
    # BNK
    'BANK': ('Bancos y Cajas', 'Bancos – catalogo'),
    'BANK_ACCOUNT': ('Bancos y Cajas', 'Cuentas bancarias'),
    'BANK_BRANCH': ('Bancos y Cajas', 'Sucursales bancarias'),
    'BANK_CHECK': ('Bancos y Cajas', 'Cheques'),
    'CHECK': ('Bancos y Cajas', 'Cheques'),
    'CHECKBOOK': ('Bancos y Cajas', 'Chequeras'),
    'CASH': ('Bancos y Cajas', 'Cajas'),
    'CASH_AUDIT': ('Bancos y Cajas', 'Arqueos de caja'),
    'BNK_RECONCILIATION': ('Bancos y Cajas', 'Conciliacion bancaria'),
    'BANK_RECONCILIATION': ('Bancos y Cajas', 'Conciliacion bancaria'),
    'FINANCIAL_MOVEMENT': ('Bancos y Cajas', 'Movimientos financieros'),
    'CASH_FLOW_PROJECTION': ('Bancos y Cajas', 'Proyecciones de flujo'),
    # CG
    'JOURNAL_ENTRY': ('Contabilidad General', 'Comprobantes contables'),
    'VOUCHER': ('Contabilidad General', 'Comprobantes contables'),
    'ACCOUNTING_PERIOD': ('Contabilidad General', 'Periodos contables'),
    'CLOSING': ('Contabilidad General', 'Cierres'),
    'BOOK': ('Contabilidad General', 'Libro Diario'),
    'LIBRO_DIARIO': ('Contabilidad General', 'Libro Diario'),
    'LIBRO_MAYOR': ('Contabilidad General', 'Libro Mayor'),
    'BALANCE': ('Contabilidad General', 'Balance de comprobacion'),
    'FINANCIAL_STATEMENT': ('Contabilidad General', 'Estados financieros'),
    'STATEMENT': ('Contabilidad General', 'Estados financieros'),
    'PUC_VALIDATION': ('Contabilidad General', 'Validacion PUC'),
    'TAX_REPORT': ('Contabilidad General', 'Reportes tributarios'),
    'DIAN_REPORT': ('Contabilidad General', 'Reportes DIAN exogena'),
    'JOURNAL_ENTRY_SUPPORT': ('Contabilidad General', 'Soportes comprobante'),
    # ACT
    'ASSET': ('Activos Fijos', 'Activos'),
    'ASSET_DISPOSAL': ('Activos Fijos', 'Activos'),
    'DEPRECIATION': ('Activos Fijos', 'Depreciacion'),
    'NIIF_ALERT': ('Activos Fijos', 'Verificacion NIIF'),
    'NIIF': ('Activos Fijos', 'Verificacion NIIF'),
    'ASSET_REPORT': ('Activos Fijos', 'Reportes de activos'),
    'ASSET_ANNUAL_REVIEW': ('Activos Fijos', 'Revision anual NIIF'),
    # TER
    'THIRD_PARTY': ('Terceros', 'Terceros – catalogo'),
    'COMMERCIAL_DATA': ('Terceros', 'Datos comerciales'),
    'ECL_SEGMENT': ('Terceros', 'Segmentacion riesgo'),
    'ECL_SEGMENTATION': ('Terceros', 'Segmentacion riesgo'),
    'THIRD_PARTY_BANK_ACCOUNT': ('Terceros', 'Cuentas bancarias del tercero'),
    'THIRD_PARTY_ATTACHMENT': ('Terceros', 'Adjuntos tercero'),
    # CFG
    'ACCOUNTING_ACCOUNT': ('Listas Contables', 'Cuentas contables'),
    'COST_CENTER': ('Listas Contables', 'Centros de costo'),
    'CHART_OF_ACCOUNT': ('Listas Contables', 'PUC'),
    'PUC': ('Listas Contables', 'PUC'),
    'CURRENCY_TYPE': ('Listas Contables', 'Monedas'),
    'CURRENCY': ('Listas Contables', 'Monedas'),
    'EXCHANGE_RATE': ('Listas Contables', 'Tasas de cambio'),
    'RULER_TAX': ('Listas Contables', 'Reglas tributarias'),
    'TAX_RULE': ('Listas Contables', 'Reglas tributarias'),
    'DEPRECIATION_RULE': ('Listas Contables', 'Reglas de depreciacion'),
    'DEPRETATION_RULE': ('Listas Contables', 'Reglas de depreciacion'),
    'PAYMENT_FORM': ('Listas Contables', 'Formas de pago'),
    'WITHHOLDING': ('Listas Contables', 'Retenciones'),
    'SYSTEM_WITHHOLDING_ASSIGNMENT': ('Listas Contables', 'Asignacion retenciones sistema'),
    'ACCOUNT_MAPPING': ('Listas Contables', 'Mapeo cuentas'),
    # NOM
    'EMPLOYEE': ('Nomina', 'Empleados'),
    'PAYROLL': ('Nomina', 'Liquidacion de nomina'),
    'PAYROLL_RECEIPT': ('Nomina', 'Comprobantes de nomina'),
    'PAYROLL_CONCEPT': ('Nomina', 'Conceptos de nomina'),
    'PILA': ('Nomina', 'Reporte PILA'),
    'BENEFIT_LIQUIDATION': ('Nomina', 'Liquidacion de prestaciones'),
    # AU
    'AUDIT': ('Auditoria', 'Log de auditoria'),
    'AUDIT_LOG': ('Auditoria', 'Log de auditoria'),
    'AUDIT_RISK_RULE': ('Auditoria', 'Reglas de riesgo'),
    'RETENTION_POLICY': ('Auditoria', 'Politicas de retencion'),
    'AUDIT_FINDING': ('Auditoria', 'Hallazgos'),
    # INT
    'AAEF_BATCH': ('Integracion AAEF', 'Lotes AAEF'),
    'BATCH': ('Integracion AAEF', 'Lotes AAEF'),
    'INTEGRATION_TRANSFER': ('Integracion AAEF', 'Transferencias'),
    'TRANSFER': ('Integracion AAEF', 'Transferencias'),
    'INTEGRATION_HEALTH': ('Integracion AAEF', 'Salud integracion'),
    # PAR
    'USER': ('Parametrizacion', 'Usuarios'),
    'ROLE': ('Parametrizacion', 'Roles'),
    'PERMISSION': ('Parametrizacion', 'Permisos'),
    'MENU': ('Parametrizacion', 'Menus'),
    'MODULE': ('Parametrizacion', 'Modulos'),
    'MODULES_MENU': ('Parametrizacion', 'Menus'),
    'PARAMETER': ('Parametrizacion', 'Parametros'),
    'REPORT_TYPE': ('Parametrizacion', 'Tipos de reporte'),
    'REPORT_TEMPLATE': ('Parametrizacion', 'Plantillas de reporte'),
    'REPORTS_TYPES': ('Parametrizacion', 'Tipos de reporte'),
    'REPORTS_TEMPLATES': ('Parametrizacion', 'Plantillas de reporte'),
    'COUNTRY': ('Parametrizacion', 'Paises'),
    'MUNICIPALITY': ('Parametrizacion', 'Municipios'),
    'MENU_PERMISSION': ('Parametrizacion', 'Permisos de menu'),
    # PLAT
    'COMPANY': ('Plataforma', 'Empresas'),
    'PLATFORM_USER': ('Plataforma', 'Usuarios plataforma'),
    'PLATFORM_DASHBOARD': ('Plataforma', 'Dashboard plataforma'),
    'JWT_AUDIT': ('Plataforma', 'Auditoria JWT'),
    'JWT_CONFIG': ('Plataforma', 'Configuracion JWT'),
    # Cross
    'ACCOUNTING': ('Contabilidad General', 'Comprobantes contables'),
    'INTEGRATION': ('Integracion AAEF', 'Lotes AAEF'),
    'AUDITORIA': ('Auditoria', 'Log de auditoria'),
}

# Diccionario con permisos del glosario indexados por (mod, sub, action)
glos_index = {}
glos_by_code = {}
for p in glosario:
    glos_by_code[p['code']] = p
    key = (p['module'], (p['submodule'] or '').strip(), (p['action'] or '').strip().upper())
    glos_index[key] = p['code']

# Normalizar acentos para comparacion
import unicodedata
def normalize(s):
    if s is None: return ''
    return unicodedata.normalize('NFKD', s).encode('ASCII', 'ignore').decode('ASCII').lower().strip()

# Indice glosario alternativo: normalizar todos los componentes
glos_index_norm = {}
for p in glosario:
    sub = (p['submodule'] or '').strip()
    act = (p['action'] or '').strip().upper()
    key = (normalize(p['module']), normalize(sub), normalize(act))
    glos_index_norm[key] = p['code']

# === Procesar cada legacy ===
matched = {}
ambiguous = {}
unmatched = []

# Verbos compuestos primero (BULK_STORE, CHANGE_STATUS, etc) ordenados por longitud desc
COMPOUND_VERBS = sorted([k for k in VERB_TO_ACTION if '_' in k], key=len, reverse=True)
SIMPLE_VERBS = sorted([k for k in VERB_TO_ACTION if '_' not in k], key=len, reverse=True)

def split_legacy(code):
    """Split PERM_<VERB>_<ENTITY>. Devuelve (verb, entity_str)."""
    body = code.replace('PERM_', '', 1)
    # Probar verbos compuestos primero
    for v in COMPOUND_VERBS:
        if body.startswith(v + '_'):
            return v, body[len(v)+1:]
        if body == v:
            return v, ''
    for v in SIMPLE_VERBS:
        if body.startswith(v + '_'):
            return v, body[len(v)+1:]
        if body == v:
            return v, ''
    return None, body

def resolve_action(verb, entity_hint=''):
    """VERB legacy -> accion glosario."""
    if verb in VERB_TO_ACTION:
        return VERB_TO_ACTION[verb]
    return None

def find_glossary_entry(verb, entity):
    """Busca el code glosario que corresponda."""
    if entity not in ENTITY_TO_MODSUB:
        return None, None
    mod, sub = ENTITY_TO_MODSUB[entity]
    accion = resolve_action(verb, entity)
    if accion is None:
        return None, (mod, sub)
    # Buscar match por (mod, sub, accion) literal en glosario
    candidates = []
    for p in glosario:
        if p['module'] == mod and (p['submodule'] or '').strip() == sub:
            if (p['action'] or '').strip().upper() == accion:
                candidates.append(p['code'])
    if len(candidates) == 1:
        return candidates[0], (mod, sub)
    if len(candidates) > 1:
        return ('AMBIGUOUS', candidates), (mod, sub)
    return None, (mod, sub)

for code in legacy_codes:
    verb, entity = split_legacy(code)
    if verb is None:
        unmatched.append({'code': code, 'reason': 'verb_not_recognized'})
        continue
    if entity not in ENTITY_TO_MODSUB:
        # Probar reduciendo entidad: AP_PAYMENT_BULK -> AP_PAYMENT
        # Primero buscar prefijo mas largo
        found = False
        for k in sorted(ENTITY_TO_MODSUB.keys(), key=len, reverse=True):
            if entity.startswith(k):
                entity_used = k
                found = True
                break
        if not found:
            unmatched.append({'code': code, 'reason': f'entity_not_mapped: {entity}', 'verb': verb})
            continue
        entity = entity_used
    result, modsub = find_glossary_entry(verb, entity)
    if result is None:
        unmatched.append({'code': code, 'reason': 'no_glossary_match', 'verb': verb, 'entity': entity, 'modsub': modsub})
    elif isinstance(result, tuple) and result[0] == 'AMBIGUOUS':
        ambiguous[code] = result[1]
    else:
        matched[code] = result

print(f"Total legacy: {len(legacy_codes)}")
print(f"  matched: {len(matched)}")
print(f"  ambiguous: {len(ambiguous)}")
print(f"  unmatched: {len(unmatched)}")

# Save
with OUTPUT_MAPPING.open('w', encoding='utf-8') as f:
    json.dump({'matched': matched, 'ambiguous': ambiguous, 'unmatched': unmatched}, f, indent=2, ensure_ascii=False)

print(f"\nMapping guardado en: {OUTPUT_MAPPING}")
print(f"\n=== UNMATCHED (necesitan revision manual) ===")
for u in unmatched[:30]:
    print(f"  {u}")
print(f"\n=== AMBIGUOUS (multiples candidatos) ===")
for code, candidates in list(ambiguous.items())[:10]:
    print(f"  {code} -> {candidates}")
