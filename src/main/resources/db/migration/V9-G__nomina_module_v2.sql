-- V9-G: Modulo Nomina v2 (standalone, sin integracion AAEF payroll).
--
-- Reincorpora el modulo NOM despues de la limpieza V9-F que lo habia eliminado
-- junto con el bloque payroll del borrador AAEF. Las HUs oficiales actualizadas
-- (HU-NOM-01 a HU-NOM-06) confirman que la nomina es un requerimiento valido
-- del proyecto, solo que se opera 100% standalone (no se recibe via AAEF).
--
-- Cubre:
--   HU-NOM-01: empleados + historial salarial + validacion SMLV
--   HU-NOM-02: conceptos de nomina con cuentas PUC
--   HU-NOM-03: liquidacion periodica con asiento contable
--   HU-NOM-04: flujo BORRADOR -> APROBADA -> CERRADA
--   HU-NOM-05: prestaciones sociales (cesantias, prima, liquidacion contrato)
--   HU-NOM-06: comprobantes individuales + PILA + resumen contable por CC
--
-- Idempotente: IF NOT EXISTS en todas las tablas e indices. WHERE NOT EXISTS
-- en todos los seeds.

-- ==========================================================================
-- 1. Empleados (vinculados a terceros, HU-NOM-01)
-- ==========================================================================
CREATE TABLE IF NOT EXISTS employees (
    id                  BIGSERIAL PRIMARY KEY,
    third_party_id      BIGINT REFERENCES third_parties(id),
    document_type       VARCHAR(10) NOT NULL,
    document_number     VARCHAR(50) NOT NULL,
    full_name           VARCHAR(200) NOT NULL,
    position            VARCHAR(150),
    contract_type       VARCHAR(30),
    base_salary         NUMERIC(20,2) NOT NULL,
    hire_date           DATE,
    termination_date    DATE,
    eps                 VARCHAR(150),
    pension_fund        VARCHAR(150),
    arl                 VARCHAR(150),
    compensation_box    VARCHAR(150),
    cost_center_id      BIGINT REFERENCES cost_centers(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_employees_document
    ON employees (document_type, document_number) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_employees_third_party
    ON employees (third_party_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_employees_status
    ON employees (status) WHERE deleted_at IS NULL;

-- ==========================================================================
-- 2. Historial de cambios salariales (HU-NOM-01 E3)
-- ==========================================================================
CREATE TABLE IF NOT EXISTS employee_salary_history (
    id                  BIGSERIAL PRIMARY KEY,
    employee_id         BIGINT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    previous_salary     NUMERIC(20,2) NOT NULL,
    new_salary          NUMERIC(20,2) NOT NULL,
    effective_date      DATE NOT NULL,
    reason              VARCHAR(500) NOT NULL,
    changed_by          VARCHAR(150),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS idx_salary_history_employee_date
    ON employee_salary_history (employee_id, effective_date DESC) WHERE deleted_at IS NULL;

-- ==========================================================================
-- 3. Conceptos de nomina (HU-NOM-02)
-- ==========================================================================
CREATE TABLE IF NOT EXISTS payroll_concepts (
    id                              BIGSERIAL PRIMARY KEY,
    code                            VARCHAR(50) NOT NULL,
    name                            VARCHAR(200) NOT NULL,
    concept_type                    VARCHAR(30) NOT NULL, -- EARNING | DEDUCTION | EMPLOYER_CONTRIBUTION
    base_calculation                VARCHAR(30),          -- SALARY | IBC | FIXED | CUSTOM
    percentage                      NUMERIC(10,4),
    fixed_amount                    NUMERIC(20,2),
    formula_expression              TEXT,                 -- Expresion opcional
    accounting_account_debit_id     BIGINT REFERENCES accounting_accounts(id),
    accounting_account_credit_id    BIGINT REFERENCES accounting_accounts(id),
    legal_reference                 VARCHAR(100),
    status                          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at                      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at                      TIMESTAMP NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_payroll_concepts_code
    ON payroll_concepts (code) WHERE deleted_at IS NULL;

-- ==========================================================================
-- 4. Recibos de nomina (HU-NOM-03, HU-NOM-04)
-- ==========================================================================
CREATE TABLE IF NOT EXISTS payroll_receipts (
    id                              BIGSERIAL PRIMARY KEY,
    employee_id                     BIGINT NOT NULL REFERENCES employees(id),
    period_year                     INT NOT NULL,
    period_month                    INT NOT NULL,
    period_type                     VARCHAR(20) NOT NULL,  -- MONTHLY | BIWEEKLY
    period_start                    DATE,
    period_end                      DATE,
    days_worked                     INT NOT NULL DEFAULT 30,
    total_earnings                  NUMERIC(20,2) NOT NULL DEFAULT 0,
    total_deductions                NUMERIC(20,2) NOT NULL DEFAULT 0,
    total_employer_contributions    NUMERIC(20,2) NOT NULL DEFAULT 0,
    net_pay                         NUMERIC(20,2) NOT NULL DEFAULT 0,
    status                          VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT | APPROVED | CLOSED
    journal_entry_id                BIGINT,
    approved_by                     VARCHAR(150),
    approved_at                     TIMESTAMP,
    closed_by                       VARCHAR(150),
    closed_at                       TIMESTAMP,
    notes                           VARCHAR(500),
    created_at                      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at                      TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS idx_receipts_period
    ON payroll_receipts (period_year, period_month) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_receipts_employee_period
    ON payroll_receipts (employee_id, period_year, period_month) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_receipts_status
    ON payroll_receipts (status) WHERE deleted_at IS NULL;

-- ==========================================================================
-- 5. Lineas de concepto de cada recibo (HU-NOM-03)
-- ==========================================================================
CREATE TABLE IF NOT EXISTS payroll_lines (
    id                  BIGSERIAL PRIMARY KEY,
    receipt_id          BIGINT NOT NULL REFERENCES payroll_receipts(id) ON DELETE CASCADE,
    concept_code        VARCHAR(50) NOT NULL,
    concept_name        VARCHAR(200) NOT NULL,
    line_type           VARCHAR(30) NOT NULL,      -- EARNING | DEDUCTION | EMPLOYER_CONTRIBUTION
    amount              NUMERIC(20,2) NOT NULL,
    line_order          INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS idx_payroll_lines_receipt
    ON payroll_lines (receipt_id) WHERE deleted_at IS NULL;

-- ==========================================================================
-- 6. Tabla de retencion en la fuente parametrizable (HU-NOM-03 E2)
-- ==========================================================================
CREATE TABLE IF NOT EXISTS payroll_retention_brackets (
    id                  BIGSERIAL PRIMARY KEY,
    tax_year            INT NOT NULL,
    uvt_min             NUMERIC(15,4) NOT NULL,
    uvt_max             NUMERIC(15,4),
    marginal_rate       NUMERIC(6,4) NOT NULL,        -- ej 0.19 = 19%
    uvt_offset          NUMERIC(15,4) NOT NULL DEFAULT 0,
    fixed_uvt_amount    NUMERIC(15,4) NOT NULL DEFAULT 0,
    description         VARCHAR(200),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS idx_retention_year
    ON payroll_retention_brackets (tax_year) WHERE deleted_at IS NULL;

-- ==========================================================================
-- 7. Seed: conceptos legales colombianos precargados (HU-NOM-02 E2)
-- ==========================================================================
INSERT INTO payroll_concepts (code, name, concept_type, base_calculation, percentage, legal_reference, status, created_at, updated_at)
SELECT 'SALARIO_BASE', 'Salario base', 'EARNING', 'FIXED', NULL, 'CST Art. 127', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payroll_concepts WHERE code = 'SALARIO_BASE');

INSERT INTO payroll_concepts (code, name, concept_type, base_calculation, percentage, legal_reference, status, created_at, updated_at)
SELECT 'HORAS_EXTRA', 'Horas extras', 'EARNING', 'FIXED', NULL, 'CST Art. 159', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payroll_concepts WHERE code = 'HORAS_EXTRA');

INSERT INTO payroll_concepts (code, name, concept_type, base_calculation, percentage, legal_reference, status, created_at, updated_at)
SELECT 'BONIFICACIONES', 'Bonificaciones', 'EARNING', 'FIXED', NULL, NULL, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payroll_concepts WHERE code = 'BONIFICACIONES');

INSERT INTO payroll_concepts (code, name, concept_type, base_calculation, percentage, legal_reference, status, created_at, updated_at)
SELECT 'AUX_TRANSPORTE', 'Auxilio de transporte', 'EARNING', 'FIXED', NULL, 'Ley 15/1959', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payroll_concepts WHERE code = 'AUX_TRANSPORTE');

-- Deducciones empleado
INSERT INTO payroll_concepts (code, name, concept_type, base_calculation, percentage, legal_reference, status, created_at, updated_at)
SELECT 'SALUD_EMPLEADO', 'Aporte salud empleado', 'DEDUCTION', 'IBC', 4.00, 'Ley 100/1993 Art. 204', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payroll_concepts WHERE code = 'SALUD_EMPLEADO');

INSERT INTO payroll_concepts (code, name, concept_type, base_calculation, percentage, legal_reference, status, created_at, updated_at)
SELECT 'PENSION_EMPLEADO', 'Aporte pension empleado', 'DEDUCTION', 'IBC', 4.00, 'Ley 100/1993 Art. 20', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payroll_concepts WHERE code = 'PENSION_EMPLEADO');

INSERT INTO payroll_concepts (code, name, concept_type, base_calculation, percentage, legal_reference, status, created_at, updated_at)
SELECT 'RETE_FUENTE', 'Retencion en la fuente', 'DEDUCTION', 'CUSTOM', NULL, 'ET Art. 383', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payroll_concepts WHERE code = 'RETE_FUENTE');

-- Aportes patronales
INSERT INTO payroll_concepts (code, name, concept_type, base_calculation, percentage, legal_reference, status, created_at, updated_at)
SELECT 'SALUD_EMPRESA', 'Aporte salud empresa', 'EMPLOYER_CONTRIBUTION', 'IBC', 8.50, 'Ley 100/1993 Art. 204', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payroll_concepts WHERE code = 'SALUD_EMPRESA');

INSERT INTO payroll_concepts (code, name, concept_type, base_calculation, percentage, legal_reference, status, created_at, updated_at)
SELECT 'PENSION_EMPRESA', 'Aporte pension empresa', 'EMPLOYER_CONTRIBUTION', 'IBC', 12.00, 'Ley 100/1993 Art. 20', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payroll_concepts WHERE code = 'PENSION_EMPRESA');

INSERT INTO payroll_concepts (code, name, concept_type, base_calculation, percentage, legal_reference, status, created_at, updated_at)
SELECT 'SENA', 'Aporte SENA', 'EMPLOYER_CONTRIBUTION', 'IBC', 2.00, 'Ley 21/1982', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payroll_concepts WHERE code = 'SENA');

INSERT INTO payroll_concepts (code, name, concept_type, base_calculation, percentage, legal_reference, status, created_at, updated_at)
SELECT 'ICBF', 'Aporte ICBF', 'EMPLOYER_CONTRIBUTION', 'IBC', 3.00, 'Ley 789/2002', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payroll_concepts WHERE code = 'ICBF');

INSERT INTO payroll_concepts (code, name, concept_type, base_calculation, percentage, legal_reference, status, created_at, updated_at)
SELECT 'CAJA_COMP', 'Aporte caja de compensacion', 'EMPLOYER_CONTRIBUTION', 'IBC', 4.00, 'Ley 21/1982', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payroll_concepts WHERE code = 'CAJA_COMP');

-- Prestaciones
INSERT INTO payroll_concepts (code, name, concept_type, base_calculation, percentage, legal_reference, status, created_at, updated_at)
SELECT 'CESANTIAS', 'Cesantias', 'EMPLOYER_CONTRIBUTION', 'IBC', 8.33, 'CST Art. 249', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payroll_concepts WHERE code = 'CESANTIAS');

INSERT INTO payroll_concepts (code, name, concept_type, base_calculation, percentage, legal_reference, status, created_at, updated_at)
SELECT 'PRIMA', 'Prima de servicios', 'EMPLOYER_CONTRIBUTION', 'IBC', 8.33, 'CST Art. 306', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payroll_concepts WHERE code = 'PRIMA');

INSERT INTO payroll_concepts (code, name, concept_type, base_calculation, percentage, legal_reference, status, created_at, updated_at)
SELECT 'VACACIONES', 'Vacaciones', 'EMPLOYER_CONTRIBUTION', 'IBC', 4.17, 'CST Art. 186', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payroll_concepts WHERE code = 'VACACIONES');

INSERT INTO payroll_concepts (code, name, concept_type, base_calculation, percentage, legal_reference, status, created_at, updated_at)
SELECT 'INTERESES_CESANTIAS', 'Intereses sobre cesantias', 'EMPLOYER_CONTRIBUTION', 'CUSTOM', 12.00, 'Ley 52/1975', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payroll_concepts WHERE code = 'INTERESES_CESANTIAS');

-- ==========================================================================
-- 7b. Seed: brackets de retencion en la fuente para salarios (HU-NOM-03 E2)
--
-- Referencia: Estatuto Tributario Art. 383 (modificado por Ley 2277/2022).
-- Los 7 rangos son oficiales y publicos, parametrizables por año gravable.
-- El admin puede agregar/modificar rangos para años futuros desde la UI.
--
-- Formula aplicada por RetentionCalculationService:
--   retencion_uvt = max(0, (ingreso_uvt - uvt_offset) * marginal_rate + fixed_uvt_amount)
--   retencion_cop = retencion_uvt * UVT_vigente
--
-- Rangos Art. 383 ET (vigentes desde 2023 en adelante):
--   >0   a  95   -> 0%      (uvt_offset=0,   fixed=0)
--   >95  a  150  -> 19%     (uvt_offset=95,  fixed=0)
--   >150 a  360  -> 28%     (uvt_offset=150, fixed=10)
--   >360 a  640  -> 33%     (uvt_offset=360, fixed=69)
--   >640 a  945  -> 35%     (uvt_offset=640, fixed=162)
--   >945 a  2300 -> 37%     (uvt_offset=945, fixed=268)
--   >2300        -> 39%     (uvt_offset=2300, fixed=770)
-- ==========================================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM payroll_retention_brackets WHERE tax_year = 2026 AND deleted_at IS NULL) THEN
        INSERT INTO payroll_retention_brackets (tax_year, uvt_min, uvt_max, marginal_rate, uvt_offset, fixed_uvt_amount, description, created_at) VALUES
            (2026, 0.0000,    95.0000,   0.0000, 0.0000,    0.0000,   'Ingreso laboral exento (<=95 UVT) - Art. 383 ET', NOW()),
            (2026, 95.0001,   150.0000,  0.1900, 95.0000,   0.0000,   '19% marginal sobre exceso de 95 UVT - Art. 383 ET', NOW()),
            (2026, 150.0001,  360.0000,  0.2800, 150.0000,  10.0000,  '28% marginal sobre exceso de 150 UVT + 10 UVT fijo - Art. 383 ET', NOW()),
            (2026, 360.0001,  640.0000,  0.3300, 360.0000,  69.0000,  '33% marginal sobre exceso de 360 UVT + 69 UVT fijo - Art. 383 ET', NOW()),
            (2026, 640.0001,  945.0000,  0.3500, 640.0000,  162.0000, '35% marginal sobre exceso de 640 UVT + 162 UVT fijo - Art. 383 ET', NOW()),
            (2026, 945.0001,  2300.0000, 0.3700, 945.0000,  268.0000, '37% marginal sobre exceso de 945 UVT + 268 UVT fijo - Ley 2277/2022', NOW()),
            (2026, 2300.0001, NULL,      0.3900, 2300.0000, 770.0000, '39% marginal sobre exceso de 2300 UVT + 770 UVT fijo - Ley 2277/2022', NOW());
    END IF;
END $$;

-- ==========================================================================
-- 8. Seed: 4 conceptos contables obligatorios (NOMINA_*) en account_mappings
-- ==========================================================================
-- Nota: accounting_accounts no tiene columna "code". El codigo PUC vive en
-- cfg_chart_of_accounts(account_code), y accounting_accounts.puc_id referencia
-- esa tabla. Recreamos la funcion helper ensure_accounting_account_for_puc
-- (V9-3 la droppea al final, por eso debemos recrearla aqui) que garantiza
-- que exista la cuenta contable para el PUC solicitado. Si no existe, la crea.
CREATE OR REPLACE FUNCTION ensure_accounting_account_for_puc(
    p_puc_code VARCHAR,
    p_custom_name VARCHAR,
    p_nature VARCHAR
) RETURNS BIGINT AS $$
DECLARE
    v_puc_id BIGINT;
    v_account_id BIGINT;
    v_currency_id BIGINT;
BEGIN
    SELECT id INTO v_puc_id
      FROM cfg_chart_of_accounts
     WHERE account_code = p_puc_code AND deleted_at IS NULL
     ORDER BY id LIMIT 1;

    IF v_puc_id IS NULL THEN
        RAISE EXCEPTION 'V9-G: PUC % no existe en cfg_chart_of_accounts', p_puc_code;
    END IF;

    SELECT id INTO v_account_id
      FROM accounting_accounts
     WHERE puc_id = v_puc_id AND deleted_at IS NULL
     ORDER BY id LIMIT 1;

    IF v_account_id IS NOT NULL THEN
        RETURN v_account_id;
    END IF;

    SELECT id INTO v_currency_id
      FROM cfg_currency_types
     WHERE deleted_at IS NULL
     ORDER BY id LIMIT 1;

    INSERT INTO accounting_accounts (
        puc_id, custom_name, currency_type_id, cost_center_id,
        tax_rule_id, nature, status, created_at, updated_at, created_by
    ) VALUES (
        v_puc_id, LEFT(p_custom_name, 50), v_currency_id,
        NULL, NULL, p_nature, 'ACTIVE', NOW(), NOW(), NULL
    ) RETURNING id INTO v_account_id;

    RETURN v_account_id;
END;
$$ LANGUAGE plpgsql;

-- Seed de los 4 mapeos NOMINA_* usando la funcion
INSERT INTO account_mappings (concept_code, concept_description, puc_code, accounting_account_id, created_at, updated_at)
SELECT 'NOMINA_SALARIOS', 'Gastos de personal - salarios (PUC 5105)', '5105',
       ensure_accounting_account_for_puc('5105', 'Gastos de personal nomina', 'DEBIT'),
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM account_mappings WHERE concept_code = 'NOMINA_SALARIOS' AND deleted_at IS NULL);

INSERT INTO account_mappings (concept_code, concept_description, puc_code, accounting_account_id, created_at, updated_at)
SELECT 'NOMINA_CXP_EMPLEADOS', 'CxP empleados - neto a pagar (PUC 2505)', '2505',
       ensure_accounting_account_for_puc('2505', 'Salarios por pagar', 'CREDIT'),
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM account_mappings WHERE concept_code = 'NOMINA_CXP_EMPLEADOS' AND deleted_at IS NULL);

INSERT INTO account_mappings (concept_code, concept_description, puc_code, accounting_account_id, created_at, updated_at)
SELECT 'NOMINA_RETENCIONES', 'Retenciones y aportes de nomina (PUC 2370)', '2370',
       ensure_accounting_account_for_puc('2370', 'Retenciones y aportes nomina', 'CREDIT'),
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM account_mappings WHERE concept_code = 'NOMINA_RETENCIONES' AND deleted_at IS NULL);

INSERT INTO account_mappings (concept_code, concept_description, puc_code, accounting_account_id, created_at, updated_at)
SELECT 'NOMINA_CESANTIAS', 'Cesantias consolidadas por pagar (PUC 2510)', '2510',
       ensure_accounting_account_for_puc('2510', 'Cesantias consolidadas', 'CREDIT'),
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM account_mappings WHERE concept_code = 'NOMINA_CESANTIAS' AND deleted_at IS NULL);

DROP FUNCTION IF EXISTS ensure_accounting_account_for_puc(VARCHAR, VARCHAR, VARCHAR);

-- ==========================================================================
-- 9. Modulo "Nomina" + menus
-- ==========================================================================
INSERT INTO modules (name, description, url, icon, position, status, created_at, updated_at)
SELECT 'Nómina', 'Modulo de nomina - empleados, conceptos, liquidacion y prestaciones', 'nomina', 'ri-team-line', 10, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM modules WHERE LOWER(name) IN ('nomina','nómina') AND deleted_at IS NULL);

DO $$
DECLARE
    v_module_id BIGINT;
BEGIN
    SELECT id INTO v_module_id FROM modules
     WHERE LOWER(name) IN ('nomina','nómina') AND deleted_at IS NULL LIMIT 1;

    IF v_module_id IS NULL THEN
        RETURN;
    END IF;

    -- Empleados
    INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
    SELECT 'Empleados', 'ri-user-3-line', 'empleados', 10, v_module_id, 'ACTIVE', 'NOMINA_EMPLEADOS', true, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'NOMINA_EMPLEADOS' AND deleted_at IS NULL);

    -- Conceptos
    INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
    SELECT 'Conceptos de nómina', 'ri-list-settings-line', 'conceptos', 20, v_module_id, 'ACTIVE', 'NOMINA_CONCEPTOS', true, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'NOMINA_CONCEPTOS' AND deleted_at IS NULL);

    -- Liquidacion / Recibos
    INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
    SELECT 'Liquidación de nómina', 'ri-file-list-3-line', 'recibos', 30, v_module_id, 'ACTIVE', 'NOMINA_RECIBOS', true, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'NOMINA_RECIBOS' AND deleted_at IS NULL);

    -- Prestaciones
    INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
    SELECT 'Prestaciones sociales', 'ri-service-line', 'prestaciones', 40, v_module_id, 'ACTIVE', 'NOMINA_PRESTACIONES', true, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'NOMINA_PRESTACIONES' AND deleted_at IS NULL);

    -- PILA
    INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
    SELECT 'Reporte PILA', 'ri-file-download-line', 'pila', 50, v_module_id, 'ACTIVE', 'NOMINA_PILA', true, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'NOMINA_PILA' AND deleted_at IS NULL);

    -- Resumen contable
    INSERT INTO menus (label, icon, path, menu_order, module_id, status, component, visible, created_at, updated_at)
    SELECT 'Resumen contable por periodo', 'ri-bar-chart-grouped-line', 'resumen-contable', 60, v_module_id, 'ACTIVE', 'NOMINA_RESUMEN', true, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM menus WHERE component = 'NOMINA_RESUMEN' AND deleted_at IS NULL);
END $$;

-- ==========================================================================
-- 10. Parametros SMLV y UVT vigentes (parametrizables)
-- ==========================================================================
INSERT INTO parameters (name, description, value, category, status, created_at, updated_at)
SELECT 'sigcon.nomina.smlv', 'Salario minimo legal vigente (SMLV) en pesos colombianos', '1423500', 'NOMINA', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name = 'sigcon.nomina.smlv' AND deleted_at IS NULL);

INSERT INTO parameters (name, description, value, category, status, created_at, updated_at)
SELECT 'sigcon.nomina.uvt', 'Valor de la UVT (Unidad de Valor Tributario) para el año gravable vigente', '47065', 'NOMINA', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM parameters WHERE name = 'sigcon.nomina.uvt' AND deleted_at IS NULL);

-- Nota: los comentarios de auditoria se actualizan con la nota del V9-G
COMMENT ON TABLE employees IS
    'V9-G (2026-04-16): empleados de nomina vinculados a third_parties. HU-NOM-01.';
COMMENT ON TABLE payroll_concepts IS
    'V9-G (2026-04-16): catalogo de conceptos con formula + cuentas PUC. HU-NOM-02.';
COMMENT ON TABLE payroll_receipts IS
    'V9-G (2026-04-16): recibos de nomina por empleado/periodo. Flujo DRAFT->APPROVED->CLOSED. HU-NOM-03/04.';
COMMENT ON TABLE payroll_retention_brackets IS
    'V9-G (2026-04-16): tabla de retencion en la fuente parametrizable por año gravable. HU-NOM-03 E2 (Art. 383 ET).';
