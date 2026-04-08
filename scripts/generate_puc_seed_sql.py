"""
Genera migración SQL para cfg_chart_of_accounts a partir de
co_plan_unico_de_cuentas.json (ERPNext, GPLv3).

Fuente JSON:
https://github.com/frappe/erpnext/blob/develop/erpnext/accounts/doctype/account/chart_of_accounts/verified/co_plan_unico_de_cuentas.json
"""
from __future__ import annotations

import json
from collections import Counter
from pathlib import Path

SKIP_KEYS = frozenset({"account_number", "root_type", "account_type", "tax_rate", "currency"})

CLASS_BY_DIGIT = {
    "1": "ASSET",
    "2": "LIABILITY",
    "3": "EQUITY",
    "4": "REVENUE",
    "5": "EXPENSE",
    "6": "COST_OF_SALES",
    "7": "PRODUCTION_COST",
    "8": "MEMORANDUM_DEBIT",
    "9": "MEMORANDUM_CREDIT",
}

NATURE_BY_DIGIT = {
    "1": "DEBIT",
    "2": "CREDIT",
    "3": "CREDIT",
    "4": "CREDIT",
    "5": "DEBIT",
    "6": "DEBIT",
    "7": "DEBIT",
    "8": "DEBIT",
    "9": "CREDIT",
}

LEVEL_BY_LEN = {1: "CLASS", 2: "GROUP", 4: "ACCOUNT", 6: "SUBACCOUNT"}


def walk(node_name: str, data: dict, rows: list[tuple[str, str]]) -> None:
    if not isinstance(data, dict):
        return
    if "account_number" in data:
        rows.append((str(data["account_number"]).strip(), node_name))
    for key, val in data.items():
        if key in SKIP_KEYS:
            continue
        if isinstance(val, dict):
            walk(key, val, rows)


def sql_literal(s: str) -> str:
    return "'" + s.replace("'", "''") + "'"


def main() -> None:
    root = Path(__file__).resolve().parent
    json_path = root / "co_plan_unico_de_cuentas.json"
    out_path = root.parent / "src" / "main" / "resources" / "db" / "migration" / "V9__seed_puc_colombia.sql"

    with json_path.open(encoding="utf-8") as f:
        payload = json.load(f)

    rows: list[tuple[str, str]] = []
    for k, v in payload["tree"].items():
        walk(k, v, rows)

    name_counts = Counter(n for _, n in rows)

    def display_name(code: str, raw_name: str) -> str:
        name = raw_name.strip()
        if name_counts[raw_name] > 1:
            suffix = f" ({code})"
            max_base = 100 - len(suffix)
            if len(name) > max_base:
                name = name[:max_base]
            return (name + suffix)[:100]
        return name[:100]

    lines: list[str] = [
        "-- PUC Colombia completo (~2500 cuentas).",
        "-- Fuente: ERPNext verified chart co_plan_unico_de_cuentas.json (GPLv3).",
        "-- https://github.com/frappe/erpnext/tree/develop/erpnext/accounts/doctype/account/chart_of_accounts/verified",
        "",
        "INSERT INTO cfg_chart_of_accounts",
        "(account_code, account_name, account_class, account_level, account_nature, account_status, created_at, updated_at)",
        "SELECT t.account_code, t.account_name, t.account_class, t.account_level, t.account_nature, t.account_status, t.created_at, t.updated_at",
        "FROM (VALUES",
    ]

    value_rows: list[str] = []
    for code, raw_name in sorted(rows, key=lambda x: (len(x[0]), x[0])):
        if not code:
            continue
        first = code[0]
        if first not in CLASS_BY_DIGIT:
            raise SystemExit(f"Código no válido (clase): {code}")
        ln = len(code)
        if ln not in LEVEL_BY_LEN:
            raise SystemExit(f"Longitud de código no esperada: {code} ({ln})")
        acct_class = CLASS_BY_DIGIT[first]
        level = LEVEL_BY_LEN[ln]
        nature = NATURE_BY_DIGIT[first]
        disp = display_name(code, raw_name)
        value_rows.append(
            "("
            f"{sql_literal(code)}, {sql_literal(disp)}, {sql_literal(acct_class)}, "
            f"{sql_literal(level)}, {sql_literal(nature)}, 'ACTIVE'::varchar, NOW(), NOW()"
            ")"
        )

    lines.append(",\n".join(value_rows))
    lines.append(
        ") AS t(account_code, account_name, account_class, account_level, account_nature, account_status, created_at, updated_at)"
    )
    lines.append("WHERE NOT EXISTS (")
    lines.append("    SELECT 1 FROM cfg_chart_of_accounts c")
    lines.append("    WHERE c.account_code = t.account_code AND c.deleted_at IS NULL")
    lines.append(");")
    lines.append("")

    body = "\n".join(lines)
    out_path.write_text(body, encoding="utf-8")

    # Comprobar unicidad de nombres en el archivo generado
    display_names = [display_name(c, n) for c, n in rows if c]
    dup_names = [x for x, k in Counter(display_names).items() if k > 1]
    if dup_names:
        raise SystemExit(f"Nombres duplicados tras disambiguar: {dup_names[:20]}")

    print(f"Wrote {len(rows)} rows -> {out_path}")


if __name__ == "__main__":
    main()
