-- V9-C: Constraint UNIQUE(year, month) en accounting_periods.
--
-- Resuelve la deuda tecnica detectada durante el smoke test de Fase 2 (integracion AAEF):
-- AccountingPeriodService.findByYearAndMonth retorna Optional<T>, pero si la BD tiene
-- duplicados Hibernate lanza NonUniqueResultException. Esta migracion:
--   1. Limpia duplicados existentes (deja el registro con id mas bajo = mas antiguo).
--   2. Agrega UNIQUE INDEX sobre (year, month) para impedir duplicados a futuro.
--
-- Idempotente: se puede correr varias veces sin efecto secundario.

-- ==========================================================================
-- 1. Limpiar duplicados existentes
--    (deja el registro con id mas bajo, elimina los demas con el mismo year+month)
-- ==========================================================================
DELETE FROM accounting_periods a
USING accounting_periods b
WHERE a.id > b.id
  AND a.year = b.year
  AND a.month = b.month;

-- ==========================================================================
-- 2. Crear UNIQUE INDEX (equivale a UNIQUE CONSTRAINT pero idempotente)
-- ==========================================================================
CREATE UNIQUE INDEX IF NOT EXISTS uk_accounting_periods_year_month
    ON accounting_periods (year, month);

-- ==========================================================================
-- 3. Comentario para documentacion en BD
-- ==========================================================================
COMMENT ON INDEX uk_accounting_periods_year_month IS
    'V9-C: garantiza unicidad de (year, month) en accounting_periods. '
    'Resuelve NonUniqueResultException en AccountingPeriodService.findByYearAndMonth.';
