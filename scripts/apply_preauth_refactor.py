#!/usr/bin/env python
"""Aplica refactor masivo de @PreAuthorize en .java.

Para cada archivo .java que contenga `@PreAuthorize(...)` con codes legacy:
  - Si el code esta en mapping['matched'], reemplaza por el nuevo glosario.
  - Si esta en mapping['admin_only']:
      * VIEW/READ/LIST/GET/SEARCH -> isAuthenticated()
      * CREATE/UPDATE/DELETE/etc  -> hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')

Reglas:
  - El reemplazo es SOLO dentro del string del @PreAuthorize.
  - No toca otros usos del mismo string.
  - Reporta archivos modificados + total reemplazos.
"""
import json, re
from pathlib import Path

ROOT = Path(r'C:\Users\creds\Desktop\Universidad\SIGCON\desarrollo\BACKEND\src\main\java')
MAPPING = json.load(open(r'C:\Users\creds\AppData\Local\Temp\mapping_v3.json', encoding='utf-8'))

# Set rapido
MATCHED = {k: v['new'] for k, v in MAPPING['matched'].items()}
ADMIN_ONLY_CODES = {x['code'] for x in MAPPING['admin_only']}

# Identifica si el legacy code es de READ o de WRITE (para los admin_only)
def is_read_verb(code):
    body = code.replace('PERM_', '', 1)
    return any(body.startswith(v + '_') or body == v for v in ('VIEW', 'READ', 'LIST', 'GET', 'SEARCH', 'EXPORT'))

# Patron para encontrar string de @PreAuthorize: hasAuthority('PERM_X')
# Vamos a procesar archivo entero y reemplazar codes legacy uno por uno.

# Verbos legacy todos juntos
LEGACY_PATTERN = re.compile(r"PERM_[A-Z][A-Z0-9_]+(?![A-Z0-9_])")

modified_files = []
counters = {'matched': 0, 'admin_only': 0}
warnings = []

for java_file in ROOT.rglob('*.java'):
    text = java_file.read_text(encoding='utf-8')
    original = text

    # Buscar todos los matches de @PreAuthorize ... fin
    # @PreAuthorize cubre desde la anotacion hasta el cierre de su parentesis externo.
    # Aproximacion: buscar @PreAuthorize\("..."\) y procesar el contenido del string.

    def fix_preauthorize_block(match):
        full = match.group(0)  # incluye @PreAuthorize("...")
        inner = match.group(1)  # contenido del string
        # Encontrar todos los PERM_ legacy en el inner
        codes_in = LEGACY_PATTERN.findall(inner)
        if not codes_in:
            return full
        new_inner = inner
        admin_only_present = False
        # Tienen que ser legacy (no ya en formato glosario PERM_AR.FACTURAS_VENTA.VER)
        for code in codes_in:
            if '.' in code:
                continue  # ya es del glosario nuevo
            if code in MATCHED:
                new_code = 'PERM_' + MATCHED[code]
                new_inner = new_inner.replace(code, new_code)
                counters['matched'] += 1
            elif code in ADMIN_ONLY_CODES:
                admin_only_present = True
                # Determinar si la expresion entera es de lectura o escritura
                # Heurista simple: el primer code legacy del bloque define el modo.
                pass
        if admin_only_present:
            # Reemplazar el ENTIRE @PreAuthorize por la version admin_only correcta
            # Si CUALQUIER code legacy es de lectura -> isAuthenticated()
            # Si todos son de escritura -> hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')
            ao_codes = [c for c in codes_in if c in ADMIN_ONLY_CODES]
            any_read = any(is_read_verb(c) for c in ao_codes)
            if any_read:
                replacement = '@PreAuthorize("isAuthenticated()")'
            else:
                replacement = "@PreAuthorize(\"hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')\")"
            counters['admin_only'] += 1
            return replacement
        # Si solo habia codes matched, devolver con el inner reemplazado
        # full original es '@PreAuthorize("...")', construir nuevo
        prefix = full[:full.index('"')+1]
        suffix = full[full.rindex('"'):]
        return prefix + new_inner + suffix

    # Regex para @PreAuthorize("..."). Cubre comillas dobles. No multilinea simple.
    pattern = re.compile(r'@PreAuthorize\("((?:[^"\\]|\\.)*)"\)', re.DOTALL)
    new_text = pattern.sub(fix_preauthorize_block, text)

    if new_text != original:
        java_file.write_text(new_text, encoding='utf-8')
        modified_files.append(str(java_file.relative_to(ROOT)))

print(f"Archivos modificados: {len(modified_files)}")
print(f"Reemplazos legacy -> glosario: {counters['matched']}")
print(f"Bloques @PreAuthorize sustituidos por isAuth/AdminOnly: {counters['admin_only']}")
print(f"\nPrimeros 15 archivos:")
for f in modified_files[:15]:
    print(f"  {f}")
