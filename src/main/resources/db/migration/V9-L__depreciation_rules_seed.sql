-- V9-L: Seed de reglas de depreciación demo para activos fijos.
--
-- Sin al menos una regla ACTIVE el form de "Crear activo" no puede seleccionar
-- metodo de depreciacion. Se siembran reglas comunes alineadas con NIC 16 y
-- el Decreto 2650 (PUC colombiano):
--
--  * PUC 1524 Equipo de oficina: 10 años lineal
--  * PUC 1528 Equipo de computación y comunicación: 5 años lineal
--  * PUC 1540 Flota y equipo de transporte: 5 años lineal
--
-- Idempotente: usa ON CONFLICT DO NOTHING sobre el UNIQUE
-- (depretation_type, accounting_account_id, effective_date, active).

DO $$
DECLARE
    v_acc_oficina BIGINT;
    v_acc_computo BIGINT;
    v_acc_transporte BIGINT;
BEGIN
    -- Buscar accounting_accounts por codigo PUC. Si no existen (seed PUC no
    -- creó la cuenta), se omite la regla correspondiente.
    SELECT a.id INTO v_acc_oficina
      FROM accounting_accounts a
      JOIN cfg_chart_of_accounts c ON a.puc_id = c.id
     WHERE c.account_code = '1524'
       AND a.deleted_at IS NULL
     LIMIT 1;

    SELECT a.id INTO v_acc_computo
      FROM accounting_accounts a
      JOIN cfg_chart_of_accounts c ON a.puc_id = c.id
     WHERE c.account_code = '1528'
       AND a.deleted_at IS NULL
     LIMIT 1;

    SELECT a.id INTO v_acc_transporte
      FROM accounting_accounts a
      JOIN cfg_chart_of_accounts c ON a.puc_id = c.id
     WHERE c.account_code = '1540'
       AND a.deleted_at IS NULL
     LIMIT 1;

    IF v_acc_oficina IS NOT NULL THEN
        INSERT INTO depretation_rules
            (name, depretation_type, depretation_rate, residual_value,
             useful_life_years, description_structured, status, effective_date,
             accounting_account_id, created_at, updated_at)
        SELECT 'Equipo de oficina - Lineal 10 anios', 'LINEAR', 10.00, 0.00, 10,
               'Depreciacion lineal para equipo de oficina segun NIC 16. Vida util 10 anios, tasa 10% anual.',
               'ACTIVE', '2020-01-01', v_acc_oficina, NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM depretation_rules
             WHERE depretation_type = 'LINEAR'
               AND accounting_account_id = v_acc_oficina
               AND effective_date = '2020-01-01'
               AND deleted_at IS NULL
        );
    END IF;

    IF v_acc_computo IS NOT NULL THEN
        INSERT INTO depretation_rules
            (name, depretation_type, depretation_rate, residual_value,
             useful_life_years, description_structured, status, effective_date,
             accounting_account_id, created_at, updated_at)
        SELECT 'Equipo de computo - Lineal 5 anios', 'LINEAR', 20.00, 0.00, 5,
               'Depreciacion lineal para equipos de computo segun NIC 16. Vida util 5 anios, tasa 20% anual.',
               'ACTIVE', '2020-01-01', v_acc_computo, NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM depretation_rules
             WHERE depretation_type = 'LINEAR'
               AND accounting_account_id = v_acc_computo
               AND effective_date = '2020-01-01'
               AND deleted_at IS NULL
        );
    END IF;

    IF v_acc_transporte IS NOT NULL THEN
        INSERT INTO depretation_rules
            (name, depretation_type, depretation_rate, residual_value,
             useful_life_years, description_structured, status, effective_date,
             accounting_account_id, created_at, updated_at)
        SELECT 'Flota y transporte - Lineal 5 anios', 'LINEAR', 20.00, 10.00, 5,
               'Depreciacion lineal para flota y equipo de transporte segun NIC 16. Vida util 5 anios, valor residual 10%.',
               'ACTIVE', '2020-01-01', v_acc_transporte, NOW(), NOW()
        WHERE NOT EXISTS (
            SELECT 1 FROM depretation_rules
             WHERE depretation_type = 'LINEAR'
               AND accounting_account_id = v_acc_transporte
               AND effective_date = '2020-01-01'
               AND deleted_at IS NULL
        );
    END IF;
END $$;
