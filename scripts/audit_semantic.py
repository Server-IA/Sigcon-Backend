"""Audita semantica @PreAuthorize: endpoint vs accion del code."""
from pathlib import Path
import re

ROOT = Path(r'C:\Users\creds\Desktop\Universidad\SIGCON\desarrollo\BACKEND\src\main\java')

action_keywords = {
    'CREAR':    ['/store','/create','/new','register'],
    'EDITAR':   ['@PutMapping','/update','/edit'],
    'ELIMINAR': ['@DeleteMapping','/delete'],
    'VER':      ['@GetMapping','/search','/list','/get','/view'],
    'APROBAR':  ['/post','/approve','/aprobar'],
    'REVERSAR': ['/reverse','/reversar'],
    'ANULAR':   ['/void','/anular','/cancel'],
}

tolerance = {
    'CREAR':    ['CREAR','REGISTRAR','GENERAR','CARGA_MASIVA','LIQUIDAR','EJECUTAR_DEPRECIACION','REVALUAR','DAR_DE_BAJA','VINCULAR','EJECUTAR'],
    'EDITAR':   ['EDITAR','ACTUALIZAR','CONFIGURAR','ASIGNAR_CUENTA','CAMBIAR_ESTADO','AJUSTAR_MANUAL','APROBAR','RECHAZAR','CONCILIAR','VINCULAR','APLICAR_A_FACTURA','APLICAR_NOTA_CREDITO','LIQUIDAR','CERRAR','REINTENTAR_DOCUMENTO','EXPORTAR_PDF','IMPORTAR_EXTRACTO','EJECUTAR_MENSUAL','EJECUTAR_ANUAL','REVALUAR','CONTABILIZAR','GENERAR','EXPORTAR','EJECUTAR_DEPRECIACION','ANULAR','DAR_DE_BAJA','REGISTRAR_RECEPCION'],
    'ELIMINAR': ['ELIMINAR','BORRAR','PURGAR','DAR_DE_BAJA','ANULAR'],
    'VER':      ['VER','CONSULTAR','LISTAR','DESCARGAR','EXPORTAR','EXPORTAR_PDF','EXPORTAR_DIAN','EXPORTAR_BG','EXPORTAR_ER','EXPORTAR_FE','EXPORTAR_COMPARATIVO','EXPORTAR_IMPUESTOS','VER_DETALLE','REGISTRAR_RECEPCION'],
    'APROBAR':  ['APROBAR','CONTABILIZAR','EJECUTAR_MENSUAL','EJECUTAR_ANUAL'],
    'REVERSAR': ['REVERSAR','REVERSO','ANULAR','CORREGIR'],
    'ANULAR':   ['ANULAR','REVERSAR','CANCELAR'],
}

issues = []
for f in ROOT.rglob('*.java'):
    if 'Controller' not in f.name: continue
    text = f.read_text(encoding='utf-8')
    lines = text.split('\n')
    for i, line in enumerate(lines):
        if not re.search(r'@(Get|Post|Put|Delete|Patch)Mapping', line):
            continue
        mapping_line = line.strip()
        preauth_line = None
        method_line = None
        for j in range(i+1, min(i+15, len(lines))):
            if '@PreAuthorize' in lines[j] and preauth_line is None:
                preauth_line = lines[j].strip()
            if 'public ' in lines[j] and 'class ' not in lines[j]:
                method_line = lines[j].strip()
                break
        if not preauth_line:
            continue
        codes = re.findall(r"PERM_[A-Z][A-Z0-9._]+", preauth_line)
        if not codes:
            continue
        expected = None
        full_ctx = mapping_line + ' ' + (method_line or '')
        for action, kws in action_keywords.items():
            if any(k.lower() in full_ctx.lower() for k in kws):
                expected = action
                break
        if not expected and 'PostMapping' in mapping_line:
            if any(k in full_ctx.lower() for k in ['/search','/list']):
                expected = 'VER'
            else:
                expected = 'CREAR'
        if not expected:
            continue
        for code in codes:
            if '.' not in code:
                continue
            action_in_code = code.split('.')[-1]
            ok = tolerance.get(expected, [])
            if action_in_code not in ok:
                rel = str(f.relative_to(ROOT)).replace('\\', '/')
                issues.append({
                    'file': rel, 'line': i+1,
                    'expected': expected, 'in_code': action_in_code,
                    'mapping': mapping_line[:90],
                    'preauth': preauth_line[:130]
                })
                break

print(f"Total mismatches semanticos: {len(issues)}")
for ix in issues:
    print(f"\n  {ix['file']}:{ix['line']}")
    print(f"    expected={ix['expected']}, in_code={ix['in_code']}")
    print(f"    {ix['mapping']}")
    print(f"    {ix['preauth']}")
