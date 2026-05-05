#!/usr/bin/env python
"""Aplicar los 26 reemplazos legacy finales que escaparon al primer refactor.

Estrategia: replace simple en cada .java. Idempotente (no toca codes ya con punto).
"""
from pathlib import Path

ROOT = Path(r'C:\Users\creds\Desktop\Universidad\SIGCON\desarrollo\BACKEND\src\main\java')

# Mapping 1:1 (code legacy -> code glosario)
REPLACEMENTS = {
    'PERM_ASSIGN_ACCOUNTING_ACCOUNT_TO_RULER_TAX': 'PERM_CFG.REGLAS_TRIBUTARIAS.ASIGNAR_CUENTA',
    'PERM_CHANGE_CASH_STATUS':       'PERM_BNK.CAJAS.CAMBIAR_ESTADO',
    'PERM_CREATE_CASH':              'PERM_BNK.CAJAS.CREAR',
    'PERM_CREATE_COST_CENTER':       'PERM_CFG.CENTROS_COSTO.CREAR',
    'PERM_CREATE_DEPRETATION_RULE':  'PERM_CFG.DEPRECIACION.CREAR',
    'PERM_CREATE_JOURNAL_ENTRY':     'PERM_CG.COMPROBANTES.CREAR',
    'PERM_CREATE_RULER_TAX':         'PERM_CFG.REGLAS_TRIBUTARIAS.CREAR',
    'PERM_DELETE_COST_CENTER':       'PERM_CFG.CENTROS_COSTO.ELIMINAR',
    'PERM_DELETE_DEPRETATION_RULE':  'PERM_CFG.DEPRECIACION.ELIMINAR',
    'PERM_DELETE_RULER_TAX':         'PERM_CFG.REGLAS_TRIBUTARIAS.ELIMINAR',
    'PERM_SEARCH_VOUCHER':           'PERM_CG.COMPROBANTES.VER',
    'PERM_UPDATE_CASH':              'PERM_BNK.CAJAS.EDITAR',
    'PERM_UPDATE_COST_CENTER':       'PERM_CFG.CENTROS_COSTO.EDITAR',
    'PERM_UPDATE_DEPRETATION_RULE':  'PERM_CFG.DEPRECIACION.EDITAR',
    'PERM_UPDATE_JOURNAL_ENTRY':     'PERM_CG.COMPROBANTES.EDITAR',
    'PERM_UPDATE_RULER_TAX':         'PERM_CFG.REGLAS_TRIBUTARIAS.EDITAR',
    'PERM_VIEW_CASH':                'PERM_BNK.CAJAS.VER',
    'PERM_VIEW_COST_CENTERS':        'PERM_CFG.CENTROS_COSTO.VER',
    'PERM_VIEW_DEPRETATION_RULE':    'PERM_CFG.DEPRECIACION.VER',
    'PERM_VIEW_RULER_TAX':           'PERM_CFG.REGLAS_TRIBUTARIAS.VER',
}

# Codes que NO tienen equivalente en glosario (admin-only). El @PreAuthorize completo
# debe sustituirse manualmente. Reportamos para revisar.
ADMIN_ONLY_CODES = {
    'PERM_CREATE_ACCOUNTING_ACCOUNT',
    'PERM_DELETE_ACCOUNTING_ACCOUNT',
    'PERM_UPDATE_ACCOUNTING_ACCOUNT',
    'PERM_VIEW_ACCOUNTING_ACCOUNT',
    'PERM_DELETE_CASH',         # No existe BNK.CAJAS.ELIMINAR en glosario
    'PERM_VIEW_MODULES_MENU',   # Genérico de menus, mejor isAuthenticated
}

modified_files = set()
total_replacements = 0

for java_file in ROOT.rglob('*.java'):
    text = java_file.read_text(encoding='utf-8')
    original = text
    for legacy, new in REPLACEMENTS.items():
        if legacy in text:
            count = text.count(legacy)
            text = text.replace(legacy, new)
            total_replacements += count
            modified_files.add(str(java_file.relative_to(ROOT)))
    if text != original:
        java_file.write_text(text, encoding='utf-8')

# Detectar archivos que aun tienen ADMIN_ONLY_CODES sin reemplazar
admin_only_files = []
for java_file in ROOT.rglob('*.java'):
    text = java_file.read_text(encoding='utf-8')
    for code in ADMIN_ONLY_CODES:
        if code in text:
            admin_only_files.append((str(java_file.relative_to(ROOT)), code))

print(f"Reemplazos 1:1 aplicados: {total_replacements}")
print(f"Archivos modificados: {len(modified_files)}")
for f in sorted(modified_files):
    print(f"  {f}")
print()
print(f"=== ADMIN_ONLY_CODES pendientes ({len(admin_only_files)}) ===")
print("Estos requieren reemplazar el @PreAuthorize entero por hasAnyAuthority/isAuthenticated:")
for f, c in admin_only_files:
    print(f"  {f} -> {c}")
