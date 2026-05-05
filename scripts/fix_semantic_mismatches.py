"""Corrige los 54 mismatches semanticos detectados.

Estrategia: para cada @PreAuthorize cuya accion en el code NO corresponde al
verbo HTTP del endpoint, sustituir por:

  - Si existe en BD el code con la accion correcta -> usarlo
  - Si no existe -> hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')

Reglas por tipo de mapping:
  @PostMapping plain (sin /search) o /store / /create -> CREAR
  @PostMapping /search                                 -> VER
  @PutMapping / /update                                -> EDITAR
  @DeleteMapping / /delete                             -> ELIMINAR
  Endpoints especiales:
    /post, /approve, /aprobar -> APROBAR
    /reverse, /reversar       -> REVERSAR
    /void, /anular, /cancel   -> ANULAR
    /calculate                -> AJUSTAR_MANUAL (caso ECL) o EJECUTAR_DEPRECIACION
"""
from pathlib import Path
import re
import json
import subprocess

ROOT = Path(r'C:\Users\creds\Desktop\Universidad\SIGCON\desarrollo\BACKEND\src\main\java')
ADMIN_AUTH = "@PreAuthorize(\"hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')\")"

# Cargar codes existentes en BD para verificar antes de proponer reemplazo
result = subprocess.run(
    ['docker', 'exec', 'sigcon-local-db', 'psql', '-U', 'postgres', '-d', 'backend',
     '-tAc', "SELECT code FROM permissions WHERE deleted_at IS NULL"],
    capture_output=True, text=True
)
DB_CODES = set(line.strip() for line in result.stdout.splitlines() if line.strip())
print(f"Codes en BD: {len(DB_CODES)}")

# Helper: dado un code legacy con accion incorrecta, derivar el code correcto
def derive_correct_code(code, expected_action):
    # code = "PERM_TER.TERCEROS.IMPORTAR_MASIVO" -> base = "TER.TERCEROS"
    base = code.replace('PERM_', '').rsplit('.', 1)[0]
    candidate = f'{base}.{expected_action}'
    if candidate in DB_CODES:
        return f'PERM_{candidate}'
    return None

def determine_expected(mapping_line, method_line):
    full = (mapping_line + ' ' + (method_line or '')).lower()
    if '@putmapping' in full or '/update' in full or '/edit' in full:
        return 'EDITAR'
    if '@deletemapping' in full or '/delete' in full:
        return 'ELIMINAR'
    if '/post' in full and 'postmapping' not in full.replace('@postmapping',''):
        return 'APROBAR'
    if '/{id}/post' in full or '/approve' in full or '/aprobar' in full:
        return 'APROBAR'
    if '/reverse' in full or '/reversar' in full:
        return 'REVERSAR'
    if '/void' in full or '/anular' in full or '/cancel' in full:
        return 'ANULAR'
    if '@postmapping' in full:
        if '/search' in full or '/list' in full:
            return 'VER'
        if '/calculate' in full:
            return 'CALCULAR'
        return 'CREAR'
    if '@getmapping' in full:
        return 'VER'
    return None

# Tolerancia: actions del glosario que son aceptables para cada expected
TOLERANCE = {
    'CREAR':    {'CREAR','REGISTRAR','GENERAR','CARGA_MASIVA','IMPORTAR_MASIVO','LIQUIDAR','EJECUTAR_DEPRECIACION','REVALUAR','DAR_DE_BAJA','VINCULAR','EJECUTAR'},
    'EDITAR':   {'EDITAR','ACTUALIZAR','CONFIGURAR','ASIGNAR_CUENTA','CAMBIAR_ESTADO','AJUSTAR_MANUAL','APROBAR','RECHAZAR','CONCILIAR','VINCULAR','APLICAR_A_FACTURA','APLICAR_NOTA_CREDITO','LIQUIDAR','CERRAR','REINTENTAR_DOCUMENTO','EXPORTAR_PDF','IMPORTAR_EXTRACTO','EJECUTAR_MENSUAL','EJECUTAR_ANUAL','REVALUAR','CONTABILIZAR','GENERAR','EXPORTAR','EJECUTAR_DEPRECIACION','ANULAR','DAR_DE_BAJA','REGISTRAR_RECEPCION','REGISTRAR'},
    'ELIMINAR': {'ELIMINAR','BORRAR','PURGAR','DAR_DE_BAJA','ANULAR'},
    'VER':      {'VER','CONSULTAR','LISTAR','DESCARGAR','EXPORTAR','EXPORTAR_PDF','EXPORTAR_DIAN','EXPORTAR_BG','EXPORTAR_ER','EXPORTAR_FE','EXPORTAR_COMPARATIVO','EXPORTAR_IMPUESTOS','VER_DETALLE','REGISTRAR_RECEPCION','EXPORTAR_REPORTE','GENERAR'},
    'APROBAR':  {'APROBAR','CONTABILIZAR','EJECUTAR_MENSUAL','EJECUTAR_ANUAL'},
    'REVERSAR': {'REVERSAR','REVERSO','ANULAR','CORREGIR'},
    'ANULAR':   {'ANULAR','REVERSAR','CANCELAR'},
    'CALCULAR': {'CALCULAR','AJUSTAR_MANUAL','EJECUTAR','EJECUTAR_DEPRECIACION','GENERAR'},
}

# Special action prefer-list per expected (orden de preferencia para derivacion)
PREFER_FOR_EXPECTED = {
    'CREAR':    ['CREAR','REGISTRAR','GENERAR'],
    'EDITAR':   ['EDITAR','CAMBIAR_ESTADO','CONFIGURAR'],
    'ELIMINAR': ['ELIMINAR','DAR_DE_BAJA','ANULAR'],
    'VER':      ['VER','VER_DETALLE','LISTAR','CONSULTAR'],
    'APROBAR':  ['APROBAR','CONTABILIZAR'],
    'REVERSAR': ['REVERSAR','ANULAR'],
    'ANULAR':   ['ANULAR','REVERSAR'],
    'CALCULAR': ['AJUSTAR_MANUAL','EJECUTAR','GENERAR'],
}

fixes = 0
modified = set()
report = []

for f in ROOT.rglob('*.java'):
    if 'Controller' not in f.name: continue
    text = f.read_text(encoding='utf-8')
    original = text
    lines = text.split('\n')
    out = list(lines)

    for i, line in enumerate(lines):
        if not re.search(r'@(Get|Post|Put|Delete|Patch)Mapping', line):
            continue
        mapping_line = line.strip()
        preauth_idx = None
        method_line = None
        for j in range(i+1, min(i+15, len(lines))):
            if '@PreAuthorize' in lines[j] and preauth_idx is None:
                preauth_idx = j
            if 'public ' in lines[j] and 'class ' not in lines[j]:
                method_line = lines[j].strip()
                break
        if preauth_idx is None:
            continue
        preauth = lines[preauth_idx]
        codes = re.findall(r"PERM_[A-Z][A-Z0-9._]+", preauth)
        if not codes:
            continue
        expected = determine_expected(mapping_line, method_line)
        if not expected:
            continue
        ok_set = TOLERANCE.get(expected, set())
        # Verificar mismatch
        any_ok = False
        for c in codes:
            if '.' not in c:
                continue
            action = c.split('.')[-1]
            if action in ok_set:
                any_ok = True
                break
        if any_ok:
            continue
        # Mismatch detectado. Intentar derivar code correcto
        replacement_code = None
        for c in codes:
            if '.' not in c: continue
            for pref in PREFER_FOR_EXPECTED.get(expected, []):
                cand = derive_correct_code(c, pref)
                if cand:
                    replacement_code = cand
                    break
            if replacement_code:
                break
        # Aplicar reemplazo
        indent = preauth[:len(preauth) - len(preauth.lstrip())]
        rel = str(f.relative_to(ROOT)).replace('\\','/')
        if replacement_code:
            new_pre = f"{indent}@PreAuthorize(\"hasAuthority('{replacement_code}') or hasAuthority('ROLE_ADMIN')\")"
            report.append(f"  REPLACE {rel}:{i+1} -> {replacement_code}")
        else:
            new_pre = indent + ADMIN_AUTH
            report.append(f"  ADMIN_ONLY {rel}:{i+1} (no derivable, expected={expected})")
        out[preauth_idx] = new_pre
        fixes += 1
        modified.add(rel)

    new_text = '\n'.join(out)
    if new_text != original:
        f.write_text(new_text, encoding='utf-8')

print(f"\nFixes aplicados: {fixes}")
print(f"Archivos modificados: {len(modified)}")
print("\n=== Detalle ===")
for r in report[:60]:
    print(r)
