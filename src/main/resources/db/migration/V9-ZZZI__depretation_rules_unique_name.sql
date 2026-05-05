-- HU-CFG-RF-13/15 (Bloque AP, 2026-05-04): nombre unico per-tenant en reglas
-- de depreciacion. Antes del fix existian multiples filas con el mismo nombre
-- (ej. "OFICINA QA1" replicada por seeds + creaciones manuales) lo que confundia
-- al contador y dificultaba la trazabilidad legal.
--
-- Estrategia:
-- 1. Identificar duplicados por (company_id, name) entre filas activas
--    (deleted_at IS NULL).
-- 2. Conservar el id MAS BAJO (el original) y soft-deletar el resto.
-- 3. Crear UNIQUE INDEX parcial sobre (company_id, lower(name)) WHERE deleted_at IS NULL.
--
-- Idempotente: si no hay duplicados, no toca nada. Si el indice ya existe,
-- IF NOT EXISTS lo deja pasar.

DO $$
BEGIN
    -- Soft-delete duplicados conservando el id mas bajo por (company_id, lower(name))
    UPDATE depretation_rules d
       SET deleted_at = NOW()
     WHERE deleted_at IS NULL
       AND id NOT IN (
           SELECT MIN(id)
             FROM depretation_rules
            WHERE deleted_at IS NULL
            GROUP BY company_id, LOWER(TRIM(name))
       );
END $$;

-- Indice unico parcial (solo activas) por (company_id, name case-insensitive).
CREATE UNIQUE INDEX IF NOT EXISTS uk_depretation_rules_company_name_active
    ON depretation_rules (company_id, LOWER(TRIM(name)))
    WHERE deleted_at IS NULL;
