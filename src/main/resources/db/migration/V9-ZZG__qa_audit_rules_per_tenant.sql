-- V9-ZZG: Auto-provision de audit_risk_rules + audit_retention_policies por empresa.
-- HU-AU-04 + HU-AU-10: cada empresa nueva debe nacer con las 5 reglas de riesgo y
-- 4 politicas de retencion baseline (clonadas desde SIGCON DEMO id=1, que las tiene
-- seedeadas en V9-B).
-- Idempotente: skip si la empresa ya tiene reglas/politicas.

CREATE OR REPLACE FUNCTION _tenant_seed_audit_baseline(p_company_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    -- Skip si ya tiene reglas
    IF NOT EXISTS(SELECT 1 FROM audit_risk_rules WHERE company_id=p_company_id AND deleted_at IS NULL) THEN
        INSERT INTO audit_risk_rules (company_id, name, match_module, match_action, match_entity_type,
                                       severity, priority, enabled, description, created_at, updated_at)
        SELECT p_company_id, name, match_module, match_action, match_entity_type,
               severity, priority, enabled, description, NOW(), NOW()
          FROM audit_risk_rules WHERE company_id=1 AND deleted_at IS NULL;
        RAISE NOTICE 'Audit risk_rules clonadas para empresa=%', p_company_id;
    END IF;

    -- Skip si ya tiene politicas
    IF NOT EXISTS(SELECT 1 FROM audit_retention_policies WHERE company_id=p_company_id AND deleted_at IS NULL) THEN
        INSERT INTO audit_retention_policies (company_id, name, match_module, match_severity,
                                                retention_days, legal_basis, enabled, description,
                                                created_at, updated_at)
        SELECT p_company_id, name, match_module, match_severity,
               retention_days, legal_basis, enabled, description,
               NOW(), NOW()
          FROM audit_retention_policies WHERE company_id=1 AND deleted_at IS NULL;
        RAISE NOTICE 'Audit retention policies clonadas para empresa=%', p_company_id;
    END IF;
END $$;

-- Backfill: aplicar a TODAS las empresas activas que aun no tengan
DO $$
DECLARE r RECORD;
BEGIN
    FOR r IN SELECT id FROM companies WHERE deleted_at IS NULL AND id != 1 ORDER BY id
    LOOP
        PERFORM _tenant_seed_audit_baseline(r.id);
    END LOOP;
END $$;

-- Helper para reuso futuro (CompanyService.createWithAdmin lo invocara)
COMMENT ON FUNCTION _tenant_seed_audit_baseline(BIGINT) IS
    'Clona audit_risk_rules y audit_retention_policies de empresa id=1 (SIGCON DEMO) hacia el tenant indicado. Idempotente. Llamar al crear empresa nueva.';

SELECT 'V9-ZZG aplicado: audit baseline en todas las empresas QA' AS status;
