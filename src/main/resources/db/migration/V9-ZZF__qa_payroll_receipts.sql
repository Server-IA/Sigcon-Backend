-- V9-ZZF: Recibos de nomina del mes actual para empresas QA.
-- HU-NOM-04: cada empresa con 4 empleados debe poder ver "Liquidacion del periodo".
-- Insertamos 4 recibos en estado APPROVED + lineas (devengado/deduccion/aporte) +
-- JE asociado en POSTED. Idempotente (skip si ya existen).

CREATE OR REPLACE FUNCTION _qa_payroll_seed_company(p_company_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_year   INT  := EXTRACT(YEAR FROM CURRENT_DATE)::INT;
    v_month  INT  := EXTRACT(MONTH FROM CURRENT_DATE)::INT;
    v_period_start DATE := DATE_TRUNC('MONTH', CURRENT_DATE)::DATE;
    v_period_end   DATE := (DATE_TRUNC('MONTH', CURRENT_DATE) + INTERVAL '1 MONTH - 1 DAY')::DATE;
    v_acct_salarios   BIGINT;
    v_acct_cxp_emp    BIGINT;
    v_acct_retenciones BIGINT;
    v_period_id BIGINT;
    v_je_id BIGINT;
    r RECORD;
    v_devengado NUMERIC(20,2);
    v_salud_emp NUMERIC(20,2);
    v_pension_emp NUMERIC(20,2);
    v_neto NUMERIC(20,2);
    v_aportes_emp NUMERIC(20,2); -- patronal salud+pension+sena+icbf+caja
BEGIN
    -- Skip si ya hay recibos del mes
    IF EXISTS(SELECT 1 FROM payroll_receipts WHERE company_id=p_company_id
              AND period_year=v_year AND period_month=v_month AND deleted_at IS NULL) THEN
        RAISE NOTICE 'Recibos ya existen para empresa=%, periodo=%-%', p_company_id, v_year, v_month;
        RETURN;
    END IF;

    SELECT accounting_account_id INTO v_acct_salarios     FROM account_mappings WHERE company_id=p_company_id AND concept_code='NOMINA_SALARIOS' LIMIT 1;
    SELECT accounting_account_id INTO v_acct_cxp_emp      FROM account_mappings WHERE company_id=p_company_id AND concept_code='NOMINA_CXP_EMPLEADOS' LIMIT 1;
    SELECT accounting_account_id INTO v_acct_retenciones  FROM account_mappings WHERE company_id=p_company_id AND concept_code='NOMINA_RETENCIONES' LIMIT 1;
    IF v_acct_salarios IS NULL THEN
        RAISE NOTICE 'Sin mapeos NOMINA_* para empresa=%; skip', p_company_id;
        RETURN;
    END IF;

    SELECT id INTO v_period_id FROM accounting_periods
     WHERE company_id=p_company_id AND year=v_year AND month=v_month LIMIT 1;

    -- Por cada empleado activo crear recibo
    FOR r IN
        SELECT e.id, e.full_name, e.base_salary, e.document_number FROM employees e
         WHERE e.company_id=p_company_id AND e.deleted_at IS NULL
         ORDER BY e.id
    LOOP
        v_devengado   := r.base_salary;
        v_salud_emp   := ROUND(r.base_salary * 0.04, 2);
        v_pension_emp := ROUND(r.base_salary * 0.04, 2);
        v_neto        := v_devengado - v_salud_emp - v_pension_emp;
        v_aportes_emp := ROUND(r.base_salary * (0.085 + 0.12 + 0.02 + 0.03 + 0.04), 2); -- 28.5%

        -- 1) Crear JE consolidado (partida doble: D salarios = C neto + C retenciones)
        INSERT INTO journal_entries (company_id, entry_number, entry_date, description, status,
                                      total_debit, total_credit, source_module, source_id,
                                      period_year, period_month, fiscal_year,
                                      created_by, created_at, updated_at)
        VALUES (p_company_id,
                COALESCE((SELECT MAX(entry_number) FROM journal_entries WHERE company_id=p_company_id AND fiscal_year=v_year), 0) + 1,
                v_period_end,
                'Liquidacion nomina ' || r.full_name || ' ' || v_year || '-' || LPAD(v_month::TEXT,2,'0'),
                'POSTED',
                v_devengado, v_devengado,
                'NOM', r.id, v_year, v_month, v_year,
                'system', NOW(), NOW())
        RETURNING id INTO v_je_id;

        INSERT INTO journal_entry_lines (company_id, journal_entry_id, accounting_account_id,
                                          line_order, debit_amount, credit_amount, description, created_at)
        VALUES
          (p_company_id, v_je_id, v_acct_salarios,    1, v_devengado, 0, 'Sueldo devengado', NOW()),
          (p_company_id, v_je_id, v_acct_cxp_emp,     2, 0, v_neto, 'Neto a pagar empleado', NOW()),
          (p_company_id, v_je_id, v_acct_retenciones, 3, 0, v_salud_emp + v_pension_emp, 'Retenciones empleado', NOW());

        -- 2) Crear recibo APPROVED enlazando JE
        INSERT INTO payroll_receipts (company_id, employee_id, period_year, period_month, period_type,
                                       period_start, period_end, days_worked,
                                       total_earnings, total_deductions, total_employer_contributions, net_pay,
                                       status, journal_entry_id, approved_by, approved_at,
                                       notes, created_at, updated_at)
        VALUES (p_company_id, r.id, v_year, v_month, 'MONTHLY',
                v_period_start, v_period_end, 30,
                v_devengado, v_salud_emp + v_pension_emp, v_aportes_emp, v_neto,
                'APPROVED', v_je_id, 'system', NOW(),
                'Recibo seed QA', NOW(), NOW());

        -- 3) Lineas detalle del recibo
        INSERT INTO payroll_lines (company_id, receipt_id, line_order, line_type,
                                    concept_code, concept_name, amount, created_at)
        VALUES
          (p_company_id, currval(pg_get_serial_sequence('payroll_receipts','id')),
           1, 'EARNING', 'SUELDO_BASE', 'Sueldo basico mensual', v_devengado, NOW()),
          (p_company_id, currval(pg_get_serial_sequence('payroll_receipts','id')),
           2, 'DEDUCTION', 'SALUD_EMPLEADO', 'Aporte salud empleado 4%', v_salud_emp, NOW()),
          (p_company_id, currval(pg_get_serial_sequence('payroll_receipts','id')),
           3, 'DEDUCTION', 'PENSION_EMPLEADO', 'Aporte pension empleado 4%', v_pension_emp, NOW());
    END LOOP;

    RAISE NOTICE 'V9-ZZF empresa=%: recibos creados', p_company_id;
END $$;

-- Loop sobre empresas QA
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT id FROM companies WHERE business_name LIKE 'EMPRESA QA % SAS' AND deleted_at IS NULL ORDER BY id
    LOOP
        PERFORM _qa_payroll_seed_company(r.id);
    END LOOP;
END $$;

SELECT 'V9-ZZF aplicado: recibos nomina mes actual seedeados' AS status;
