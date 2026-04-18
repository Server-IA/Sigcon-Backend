-- V9-4: Fase 3 - Eliminacion final del modelo multi-empresa deprecado.
--
-- Contexto:
--   En Fase 0 (2026-04-12) se elimino el multi-tenant a nivel de codigo, removiendo
--   company_id de 10 entidades. Sin embargo, las tablas 'companies' y
--   'company_withholding_assignments' y las entidades Java Company.java,
--   CompanyRepository.java, CompanyWithholdingAssignment.java y
--   CompanyWithholdingAssignmentRepository.java quedaron DEPRECADAS pero
--   no se eliminaron, a la espera de Fase 3.
--
-- Fase 3 (2026-04-14):
--   - Entidades Java y repositorios deprecados ELIMINADOS del codigo.
--   - Campo inyectado 'CompanyRepository' (no usado) removido de BankAccountService.
--   - Comentario muerto '// entity.setCompany(user.getCompany());' removido de CheckbookService.
--   - Reemplazo oficial de CompanyWithholdingAssignment: SystemWithholdingAssignment
--     (tabla system_withholding_assignments, sin dependencia de Company, con
--     vigencia temporal effectiveFrom/effectiveTo y status).
--
-- Este script ejecuta el DROP de las tablas residuales. Es idempotente
-- (IF EXISTS) y ha sido verificado en BD limpia: 0 registros en ambas tablas,
-- ninguna otra tabla tiene FK hacia 'companies' fuera de 'company_withholding_assignments'.

-- ==========================================================================
-- 1. Eliminar company_withholding_assignments (depende de companies)
-- ==========================================================================
DROP TABLE IF EXISTS company_withholding_assignments CASCADE;

-- ==========================================================================
-- 2. Eliminar companies
-- ==========================================================================
DROP TABLE IF EXISTS companies CASCADE;
