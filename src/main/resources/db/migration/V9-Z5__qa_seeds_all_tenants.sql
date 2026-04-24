-- =============================================================================
-- V9-Z5: Seeds QA para TODOS los tenants activos (Bloque J-complemento)
--
-- V9-Z4 solo seedea en empresas con NIT ACME DEMO (900100200) y CONTADOR TEST
-- (800500600). Esta migracion inserta 2 clientes + 2 proveedores demo en
-- CADA empresa activa para que cualquier usuario tenant pueda ver datos
-- inmediatamente al loguearse.
--
-- Idempotente: no duplica si ya existe tercero con el codigo generado.
-- =============================================================================

DO $$
DECLARE
    c RECORD;
    v_status BIGINT;
    i INT;
    v_code VARCHAR;
    v_nit VARCHAR;
BEGIN
    SELECT id INTO v_status FROM third_party_status_catalog
     WHERE name = 'ACTIVO' AND deleted_at IS NULL LIMIT 1;
    IF v_status IS NULL THEN
        SELECT id INTO v_status FROM third_party_status_catalog
         WHERE deleted_at IS NULL ORDER BY id LIMIT 1;
    END IF;

    -- Recorrer todas las empresas activas
    FOR c IN SELECT id, business_name FROM companies
              WHERE status = 'ACTIVE' AND deleted_at IS NULL ORDER BY id
    LOOP
        -- 2 clientes
        FOR i IN 1..2 LOOP
            v_code := 'CLI-QA-C' || c.id || '-' || LPAD(i::text, 3, '0');
            v_nit := LPAD(((8000 + c.id * 10 + i))::text, 9, '0');
            INSERT INTO third_parties (company_id, third_party_code, nit, dv,
                                        business_name, status_id,
                                        created_at, updated_at)
            SELECT c.id, v_code, v_nit, (i % 10)::text,
                   'Cliente QA ' || i || ' - ' || c.business_name, v_status,
                   NOW(), NOW()
             WHERE NOT EXISTS (SELECT 1 FROM third_parties
                                WHERE company_id = c.id
                                  AND third_party_code = v_code
                                  AND deleted_at IS NULL);
        END LOOP;

        -- 2 proveedores
        FOR i IN 1..2 LOOP
            v_code := 'PROV-QA-C' || c.id || '-' || LPAD(i::text, 3, '0');
            v_nit := LPAD(((9000 + c.id * 10 + i))::text, 9, '0');
            INSERT INTO third_parties (company_id, third_party_code, nit, dv,
                                        business_name, status_id,
                                        created_at, updated_at)
            SELECT c.id, v_code, v_nit, ((i + 5) % 10)::text,
                   'Proveedor QA ' || i || ' - ' || c.business_name, v_status,
                   NOW(), NOW()
             WHERE NOT EXISTS (SELECT 1 FROM third_parties
                                WHERE company_id = c.id
                                  AND third_party_code = v_code
                                  AND deleted_at IS NULL);
        END LOOP;
    END LOOP;

    RAISE NOTICE 'V9-Z5: seeds QA aplicados en todas las empresas activas';
END $$;
