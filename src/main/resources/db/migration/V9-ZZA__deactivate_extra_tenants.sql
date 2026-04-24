-- V9-ZZA: Mantiene solo SIGCON DEMO (id=1, baseline) + test (id=3, QA full).
-- Las demas empresas de seed (ACME TEST, DISRIEGO PROD, ACME DEMO SAS,
-- CONTADOR TEST SAS) se desactivan para dejar el entorno limpio para QA.
--
-- V9-ZZA corre DESPUES de V9-ZZ (seed integral), para no bloquear el seeding.
-- Idempotente.

-- Desactivar empresas extra (por NIT)
UPDATE companies
   SET status = 'INACTIVE', updated_at = NOW()
 WHERE nit IN ('900111222', '900222333', '900100200', '800500600')
   AND status = 'ACTIVE';

-- Soft-delete de usuarios de empresas INACTIVE
UPDATE users
   SET deleted_at = NOW(), updated_at = NOW()
 WHERE company_id IN (
   SELECT id FROM companies WHERE status = 'INACTIVE' AND deleted_at IS NULL
 )
 AND deleted_at IS NULL;

SELECT 'V9-ZZA aplicado: empresas extras INACTIVE, users soft-deleted' AS status;
