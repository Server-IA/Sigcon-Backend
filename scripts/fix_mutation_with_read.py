#!/usr/bin/env python
"""Fix las 22 mutaciones (POST/store, PUT, DELETE) que quedaron con permiso .VER.

Reemplaza el @PreAuthorize de esos metodos por:
  hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')

Solo toca los endpoints listados (no modifica los GET/lectura).
"""
import re
from pathlib import Path

ROOT = Path(r'C:\Users\creds\Desktop\Universidad\SIGCON\desarrollo\BACKEND\src\main\java')
ADMIN_AUTH = '@PreAuthorize("hasAnyAuthority(\'ROLE_ADMIN_EMPRESA\',\'PLATFORM_ADMIN\')")'

# Lista de (file, line, mapping_marker) -- usamos heuristica: detectar @DeleteMapping/@PutMapping/@PostMapping(/store)
# y si la siguiente @PreAuthorize tiene .VER, reemplazar.

count = 0
modified_files = set()

for f in ROOT.rglob('*.java'):
    text = f.read_text(encoding='utf-8')
    lines = text.split('\n')
    out = list(lines)
    changed = False

    for i, line in enumerate(lines):
        is_mutation = (
            '@DeleteMapping' in line
            or '@PutMapping' in line
            or ('@PostMapping' in line and '/store' in line.lower())
        )
        if not is_mutation:
            continue
        # buscar siguientes 5 lineas para @PreAuthorize
        for j in range(i+1, min(i+6, len(lines))):
            if '@PreAuthorize' in lines[j]:
                if '.VER' in lines[j] or "'VER'" in lines[j]:
                    # Reemplazar con admin auth, conservando indentacion
                    indent = lines[j][:len(lines[j]) - len(lines[j].lstrip())]
                    out[j] = indent + ADMIN_AUTH
                    changed = True
                    count += 1
                break

    if changed:
        f.write_text('\n'.join(out), encoding='utf-8')
        modified_files.add(str(f.relative_to(ROOT)))

print(f"@PreAuthorize de mutaciones corregidos: {count}")
print(f"Archivos: {len(modified_files)}")
for ff in sorted(modified_files):
    print(f"  {ff}")
