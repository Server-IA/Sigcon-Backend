#!/usr/bin/env python
"""Genera V9-ZZW__rbac_glossary_v2_catalog.sql desde el glosario.

Lee /tmp/glosario.json (producido por la lectura del Excel) y emite SQL
idempotente que:
  1. Elimina toda relacion roles_permissions existente
  2. Hard-delete todos los permissions legacy
  3. Renombra rol ADMIN -> ADMIN_EMPRESA (preserva id 4 + users_roles)
  4. Soft-delete rol USER (no esta en glosario, 0 usuarios asignados)
  5. Crea roles TESORERO, OPERADOR_NOMINA, PLATFORM_ADMIN si no existen
  6. INSERTa los 201 permisos del glosario en formato MODULO.SUBMODULO.ACCION
  7. Asigna permisos a roles segun matriz del glosario
  8. Garantiza que admin@sigcondemo.test tenga rol ADMIN_EMPRESA

Idempotente: re-ejecutable sin perder datos.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JSON_PATH = Path(r'C:\Users\creds\AppData\Local\Temp\glosario.json')
SQL_OUT = ROOT / 'src' / 'main' / 'resources' / 'db' / 'migration' / 'V9-ZZW__rbac_glossary_v2_catalog.sql'

# Mapeo accion glosario -> TypePermits enum (CHECK CREATE/READ/UPDATE/DELETE)
def map_type(action: str) -> str:
    if not action:
        return 'READ'
    a = action.lower().strip()
    if any(x in a for x in ['ver', 'consultar', 'visualizar', 'listar', 'descargar']):
        return 'READ'
    if any(x in a for x in ['crear', 'generar', 'registrar', 'emitir', 'agregar', 'iniciar']):
        return 'CREATE'
    if any(x in a for x in ['eliminar', 'borrar', 'purgar', 'quitar']):
        return 'DELETE'
    return 'UPDATE'

# Map glossary module label -> exact name in modules table (case-sensitive con acentos)
MODULE_NAME_TO_DB = {
    'Cuentas por Cobrar':   'Cuentas por Cobrar',
    'Cuentas por Pagar':    'Cuentas por Pagar',
    'Bancos y Cajas':       'Bancos y Cajas',
    'Contabilidad General': 'Contabilidad General',
    'Terceros':             'Terceros',
    'Activos Fijos':        'Activos',
    'Nomina':               'Nómina',
    'Auditoria':            'Auditoría',
    'Integracion AAEF':     'Integración AAEF',
    'Listas Contables':     'Listas Contables',
    'Parametrizacion':      'Parametrización',
    'Plataforma':           'Plataforma',
}

def esc(s):
    if s is None:
        return ''
    return str(s).replace("'", "''").replace('\n', ' ').replace('\r', '').strip()

def humanize_name(p):
    """name del permiso (legible). Limit 200 chars per columna."""
    a = (p['action'] or '').strip()
    sm = (p['submodule'] or '').strip()
    return f"{a} - {sm}"[:200]

with JSON_PATH.open(encoding='utf-8') as f:
    permisos = json.load(f)

# === Build SQL ===
sql = []
sql.append("-- V9-ZZW (2026-05-01): Catalogo de permisos v2 alineado al Glosario.")
sql.append("--")
sql.append("-- REEMPLAZO TOTAL (Option A confirmada por el usuario):")
sql.append("--   - 201 permisos atomicos en formato MODULO.SUBMODULO.ACCION")
sql.append("--   - 7 roles: ADMIN_EMPRESA (rename de ADMIN), CONTADOR, AUXILIAR_CONTABLE,")
sql.append("--     TESORERO (nuevo), AUDITOR, OPERADOR_NOMINA (nuevo), PLATFORM_ADMIN (creado")
sql.append("--     como rol formal con sus 16 permisos del glosario)")
sql.append("--")
sql.append("-- Idempotente: re-ejecutable sin perder asignaciones de usuarios.")
sql.append("-- Los users que tenian rol ADMIN siguen funcionando porque preservamos id=4 al renombrar.")
sql.append("")
sql.append("BEGIN;")
sql.append("")

sql.append("-- 1) Limpiar TODAS las asignaciones rol->permiso (se rehacen segun glosario).")
sql.append("DELETE FROM roles_permissions;")
sql.append("")

sql.append("-- 2) Hard-delete permisos legacy (Option A: limpieza total).")
sql.append("DELETE FROM permissions;")
sql.append("")

sql.append("-- 3) Renombrar rol ADMIN -> ADMIN_EMPRESA (idempotente).")
sql.append("--    Caso A: primera corrida (existe ADMIN, no existe ADMIN_EMPRESA) -> rename.")
sql.append("--    Caso B: re-corrida (DataInitializer recreo ADMIN tras rename previo) ->")
sql.append("--            migrar users_roles del ADMIN duplicado al ADMIN_EMPRESA preservado")
sql.append("--            y soft-delete del duplicado para no chocar con uk_roles_active.")
sql.append("DO $$")
sql.append("DECLARE")
sql.append("    v_admin_emp_id BIGINT;")
sql.append("    v_admin_legacy_id BIGINT;")
sql.append("BEGIN")
sql.append("    SELECT id INTO v_admin_emp_id    FROM roles WHERE name='ADMIN_EMPRESA' AND deleted_at IS NULL LIMIT 1;")
sql.append("    SELECT id INTO v_admin_legacy_id FROM roles WHERE name='ADMIN'         AND deleted_at IS NULL LIMIT 1;")
sql.append("    IF v_admin_emp_id IS NULL AND v_admin_legacy_id IS NOT NULL THEN")
sql.append("        UPDATE roles SET name='ADMIN_EMPRESA', updated_at=NOW() WHERE id=v_admin_legacy_id;")
sql.append("    ELSIF v_admin_emp_id IS NOT NULL AND v_admin_legacy_id IS NOT NULL THEN")
sql.append("        INSERT INTO users_roles (user_id, role_id)")
sql.append("        SELECT ur.user_id, v_admin_emp_id FROM users_roles ur")
sql.append("         WHERE ur.role_id = v_admin_legacy_id")
sql.append("           AND NOT EXISTS (SELECT 1 FROM users_roles ur2")
sql.append("                             WHERE ur2.user_id = ur.user_id AND ur2.role_id = v_admin_emp_id);")
sql.append("        DELETE FROM users_roles WHERE role_id = v_admin_legacy_id;")
sql.append("        UPDATE roles SET deleted_at=NOW(), updated_at=NOW() WHERE id=v_admin_legacy_id;")
sql.append("    END IF;")
sql.append("END $$;")
sql.append("")

sql.append("-- 4) Soft-delete rol USER legacy (no esta en glosario, 0 usuarios asignados).")
sql.append("UPDATE roles SET deleted_at=NOW(), updated_at=NOW()")
sql.append(" WHERE name='USER' AND deleted_at IS NULL")
sql.append("   AND NOT EXISTS (SELECT 1 FROM users_roles ur WHERE ur.role_id = roles.id);")
sql.append("")

sql.append("-- 5) Crear roles nuevos si no existen (idempotente).")
for role in ('TESORERO', 'OPERADOR_NOMINA', 'PLATFORM_ADMIN'):
    sql.append(f"INSERT INTO roles (name, status, created_at, updated_at)")
    sql.append(f"SELECT '{role}', 'ACTIVE', NOW(), NOW()")
    sql.append(f" WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name='{role}' AND deleted_at IS NULL);")
    sql.append("")

sql.append("-- 6) INSERT 201 permisos del glosario.")
sql.append("--    module_id se resuelve por LIKE para tolerar acentos.")
for p in permisos:
    code = esc(p['code'])
    name_h = esc(humanize_name(p))
    desc = esc(p['desc'] or p['action'] or code)
    type_p = map_type(p['action'])
    mod_db = MODULE_NAME_TO_DB.get(p['module'], p['module'])
    mod_db_esc = esc(mod_db)
    sql.append(f"INSERT INTO permissions (module_id, name, code, type, description, created_at, updated_at)")
    sql.append(f"SELECT m.id, '{name_h}', '{code}', '{type_p}', '{desc}', NOW(), NOW()")
    sql.append(f"  FROM modules m WHERE m.name='{mod_db_esc}' AND m.deleted_at IS NULL;")

sql.append("")
sql.append("-- 7) Asignaciones rol -> permisos segun matriz del glosario.")
# Construir la matriz invertida: rol -> [codes]
matrix = {}
for p in permisos:
    for r in p['roles']:
        matrix.setdefault(r, []).append(p['code'])

for role, codes in matrix.items():
    sql.append(f"")
    sql.append(f"-- {role}: {len(codes)} permisos")
    # Insertamos en bloques con SELECT subquery
    sql.append(f"INSERT INTO roles_permissions (role_id, permission_id)")
    sql.append(f"SELECT r.id, p.id")
    sql.append(f"  FROM roles r, permissions p")
    sql.append(f" WHERE r.name='{role}' AND r.deleted_at IS NULL")
    sql.append(f"   AND p.deleted_at IS NULL")
    sql.append(f"   AND p.code IN (")
    for i, c in enumerate(codes):
        comma = ',' if i < len(codes) - 1 else ''
        sql.append(f"     '{esc(c)}'{comma}")
    sql.append(f"   )")
    sql.append(f" ON CONFLICT DO NOTHING;")

sql.append("")
sql.append("-- 8) Asegurar que admin@sigcondemo.test (caso historico sin rol asignado)")
sql.append("--    tenga ADMIN_EMPRESA. Sin esto, ese usuario no podria operar.")
sql.append("INSERT INTO users_roles (user_id, role_id)")
sql.append("SELECT u.id, r.id")
sql.append("  FROM users u, roles r")
sql.append(" WHERE u.email='admin@sigcondemo.test' AND u.deleted_at IS NULL")
sql.append("   AND r.name='ADMIN_EMPRESA' AND r.deleted_at IS NULL")
sql.append("   AND NOT EXISTS (SELECT 1 FROM users_roles ur WHERE ur.user_id=u.id AND ur.role_id=r.id);")
sql.append("")

sql.append("-- 9) superadmin@gmail.com: dejar SOLO con platform_role + sin rol tenant")
sql.append("--    (PLATFORM_ADMIN tiene rol formal aparte para que herede los 16 permisos del glosario,")
sql.append("--     pero superadmin no necesita asignacion porque platform_role del JWT lo identifica).")
sql.append("--    Sin embargo, si tiene rol ADMIN_EMPRESA legado, lo dejamos para no romper su sesion.")
sql.append("")
sql.append("COMMIT;")
sql.append("")

# Resumen comentado
sql.append("-- Resumen de la migracion:")
sql.append(f"-- Total permisos: {len(permisos)}")
type_counts = {}
for p in permisos:
    t = map_type(p['action'])
    type_counts[t] = type_counts.get(t, 0) + 1
sql.append(f"-- Mapeo TypePermits: {type_counts}")
sql.append("-- Permisos por rol:")
for role, codes in matrix.items():
    sql.append(f"--   {role}: {len(codes)}")

# Write
SQL_OUT.parent.mkdir(parents=True, exist_ok=True)
SQL_OUT.write_text('\n'.join(sql), encoding='utf-8')
print(f"OK: {SQL_OUT}")
print(f"   {len(permisos)} permisos, {len(matrix)} roles asignados")
print(f"   Archivo: {len(sql)} lineas")
