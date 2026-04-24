-- V9-ZZB: Vincula empleados con su tercero correspondiente.
--
-- V9-Z4 y V9-ZZ crean empleados sin third_party_id. Aunque el schema permite
-- NULL, la HU-NOM-01 exige que cada empleado tenga un tercero asociado
-- (facilita pagos de nomina, declaraciones tributarias y trazabilidad).
--
-- Estrategia idempotente: por cada empleado sin third_party_id,
--   1. Busca en terceros de la misma empresa si existe uno con el mismo NIT.
--   2. Si no existe, crea un tercero nuevo y lo enlaza.
--
-- NOTA: la asignacion de rol EMPLEADO al tercero se delega al backend
-- cuando se edita el tercero (evita conflictos de schema en seeds).
-- =============================================================================

DO $$
DECLARE
    e RECORD;
    v_tp_id BIGINT;
    v_status BIGINT;
BEGIN
    SELECT id INTO v_status FROM third_party_status_catalog
     WHERE name = 'ACTIVO' AND deleted_at IS NULL LIMIT 1;

    IF v_status IS NULL THEN
        RAISE NOTICE 'V9-ZZB skipped: catalog status ACTIVO no encontrado';
        RETURN;
    END IF;

    FOR e IN SELECT id, company_id, document_number, full_name
               FROM employees
              WHERE third_party_id IS NULL
                AND deleted_at IS NULL
    LOOP
        v_tp_id := NULL;

        -- Buscar tercero existente por NIT + company
        SELECT id INTO v_tp_id FROM third_parties
         WHERE company_id = e.company_id
           AND nit = e.document_number
           AND deleted_at IS NULL
         LIMIT 1;

        -- Crear tercero si no existe
        IF v_tp_id IS NULL THEN
            INSERT INTO third_parties (company_id, third_party_code, nit, dv,
                                         business_name, status_id,
                                         created_at, updated_at)
            VALUES (e.company_id,
                    'EMP-' || e.company_id || '-' || e.id,
                    COALESCE(e.document_number, 'EMP' || e.id),
                    '0',
                    e.full_name,
                    v_status, NOW(), NOW())
            RETURNING id INTO v_tp_id;
        END IF;

        -- Enlazar empleado al tercero
        UPDATE employees SET third_party_id = v_tp_id, updated_at = NOW()
         WHERE id = e.id;
    END LOOP;
END $$;

SELECT 'V9-ZZB aplicado: empleados vinculados a terceros' AS status;
