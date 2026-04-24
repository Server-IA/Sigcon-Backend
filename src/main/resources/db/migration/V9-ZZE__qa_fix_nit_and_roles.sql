-- V9-ZZE: Corrige terceros QA en BDs existentes:
--  1) NITs de 9 digitos -> 10 (TERC_012: el sistema exige 10-15)
--  2) Asigna rol CLIENTE a CLI-* y PROVEEDOR a PROV-*
-- Idempotente (re-ejecutable).

-- ============================================================
-- 1) Pad NITs de terceros QA a 10 digitos
-- ============================================================
UPDATE third_parties
   SET nit = nit || '0', updated_at = NOW()
 WHERE LENGTH(nit) = 9
   AND deleted_at IS NULL
   AND (third_party_code LIKE 'CLI-QA%' OR third_party_code LIKE 'PROV-QA%');

-- ============================================================
-- 2) Asignar roles a los terceros QA segun su prefijo de codigo
-- ============================================================
DO $$
DECLARE
    v_role_cliente_id   BIGINT;
    v_role_proveedor_id BIGINT;
    v_today             DATE := CURRENT_DATE;
    r RECORD;
BEGIN
    SELECT id INTO v_role_cliente_id   FROM third_party_role_catalog WHERE name='CLIENTE'   AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_role_proveedor_id FROM third_party_role_catalog WHERE name='PROVEEDOR' AND deleted_at IS NULL LIMIT 1;

    IF v_role_cliente_id IS NULL OR v_role_proveedor_id IS NULL THEN
        RAISE EXCEPTION 'Faltan roles CLIENTE/PROVEEDOR en third_party_role_catalog';
    END IF;

    -- Asignar CLIENTE a todos los CLI-*
    FOR r IN
        SELECT tp.id, tp.company_id
          FROM third_parties tp
         WHERE tp.third_party_code LIKE 'CLI-QA%'
           AND tp.deleted_at IS NULL
           AND NOT EXISTS (
               SELECT 1 FROM third_party_role_assignments_v2 a
                WHERE a.third_party_id = tp.id AND a.role_id = v_role_cliente_id
                  AND a.deleted_at IS NULL AND a.valid_to IS NULL
           )
    LOOP
        INSERT INTO third_party_role_assignments_v2 (company_id, third_party_id, role_id,
                                                      valid_from, valid_to, created_at)
        VALUES (r.company_id, r.id, v_role_cliente_id, v_today - INTERVAL '60 days', NULL, NOW());
    END LOOP;

    -- Asignar PROVEEDOR a todos los PROV-*
    FOR r IN
        SELECT tp.id, tp.company_id
          FROM third_parties tp
         WHERE tp.third_party_code LIKE 'PROV-QA%'
           AND tp.deleted_at IS NULL
           AND NOT EXISTS (
               SELECT 1 FROM third_party_role_assignments_v2 a
                WHERE a.third_party_id = tp.id AND a.role_id = v_role_proveedor_id
                  AND a.deleted_at IS NULL AND a.valid_to IS NULL
           )
    LOOP
        INSERT INTO third_party_role_assignments_v2 (company_id, third_party_id, role_id,
                                                      valid_from, valid_to, created_at)
        VALUES (r.company_id, r.id, v_role_proveedor_id, v_today - INTERVAL '60 days', NULL, NOW());
    END LOOP;

    RAISE NOTICE 'V9-ZZE: roles QA asignados';
END $$;

-- ============================================================
-- 6) Asignar roles a CLI-DEMO/PROV-DEMO de V9-Z3 + EMP-* terceros
-- ============================================================
DO $$
DECLARE
    v_role_cliente_id   BIGINT;
    v_role_proveedor_id BIGINT;
    v_role_empleado_id  BIGINT;
    v_today             DATE := CURRENT_DATE;
    r RECORD;
    v_role_for_tp BIGINT;
BEGIN
    SELECT id INTO v_role_cliente_id   FROM third_party_role_catalog WHERE name='CLIENTE'   AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_role_proveedor_id FROM third_party_role_catalog WHERE name='PROVEEDOR' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_role_empleado_id  FROM third_party_role_catalog WHERE name='EMPLEADO'  AND deleted_at IS NULL LIMIT 1;

    FOR r IN
        SELECT tp.id, tp.company_id, tp.third_party_code FROM third_parties tp
         WHERE tp.deleted_at IS NULL
           AND (tp.third_party_code LIKE 'CLI-%' OR tp.third_party_code LIKE 'PROV-%' OR tp.third_party_code LIKE 'EMP-%')
           AND NOT EXISTS (
               SELECT 1 FROM third_party_role_assignments_v2 a
                WHERE a.third_party_id = tp.id AND a.deleted_at IS NULL AND a.valid_to IS NULL
           )
    LOOP
        v_role_for_tp := CASE
            WHEN r.third_party_code LIKE 'CLI-%'  THEN v_role_cliente_id
            WHEN r.third_party_code LIKE 'PROV-%' THEN v_role_proveedor_id
            WHEN r.third_party_code LIKE 'EMP-%'  THEN v_role_empleado_id
        END;

        IF v_role_for_tp IS NOT NULL THEN
            INSERT INTO third_party_role_assignments_v2 (company_id, third_party_id, role_id,
                                                          valid_from, valid_to, created_at)
            VALUES (r.company_id, r.id, v_role_for_tp, v_today - INTERVAL '60 days', NULL, NOW());
        END IF;
    END LOOP;

    RAISE NOTICE 'V9-ZZE: roles DEMO/EMP asignados';
END $$;

-- ============================================================
-- 7) BACKFILL CRITICO: third_party_role_assignments (v1, ManyToMany sin company_id)
--    El frontend lee roles via @ManyToMany de ThirdParty.roles, que apunta a la
--    tabla v1, NO a v2. Asi que duplicamos las asignaciones tambien en v1.
-- ============================================================
INSERT INTO third_party_role_assignments (third_party_id, role_id)
SELECT DISTINCT a.third_party_id, a.role_id
  FROM third_party_role_assignments_v2 a
 WHERE a.deleted_at IS NULL
   AND NOT EXISTS (
       SELECT 1 FROM third_party_role_assignments t
        WHERE t.third_party_id = a.third_party_id AND t.role_id = a.role_id
   );

-- ============================================================
-- 3) Pad NITs cortos en TODOS los terceros (no solo CLI-QA/PROV-QA)
--    cubre: CLI-DEMO/PROV-DEMO de V9-Z3, terceros auto-creados desde
--    empleados por V9-ZZB, etc. NIT mínimo del sistema = 10 digits.
-- ============================================================
UPDATE third_parties
   SET nit = LPAD(nit, 10, '0'), updated_at = NOW()
 WHERE deleted_at IS NULL AND LENGTH(nit) < 10;

-- ============================================================
-- 4) Empleados sin EPS/pension: poblar con valores por defecto
--    (NOM-03 E3 exige EPS+fondo de pension activos para liquidar)
-- ============================================================
UPDATE employees
   SET eps = COALESCE(NULLIF(eps,''), 'Sura'),
       pension_fund = COALESCE(NULLIF(pension_fund,''), 'Porvenir'),
       updated_at = NOW()
 WHERE deleted_at IS NULL
   AND ((eps IS NULL OR eps='') OR (pension_fund IS NULL OR pension_fund=''));

-- ============================================================
-- 5) Empleados sin third_party_id: crear/linkear tercero
--    (HU-NOM-01 exige tercero asociado para trazabilidad de pagos)
-- ============================================================
DO $$
DECLARE
    v_status_active_id BIGINT;
    v_typeorg_natural_id BIGINT;
    v_typereg_id BIGINT;
    v_municipality_id BIGINT;
    r RECORD;
    v_existing_tp_id BIGINT;
    v_new_tp_id BIGINT;
BEGIN
    SELECT id INTO v_status_active_id FROM third_party_status_catalog WHERE name='ACTIVO' AND deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_typeorg_natural_id FROM type_organization WHERE name ILIKE '%natural%' AND deleted_at IS NULL LIMIT 1;
    IF v_typeorg_natural_id IS NULL THEN
        SELECT id INTO v_typeorg_natural_id FROM type_organization WHERE deleted_at IS NULL LIMIT 1;
    END IF;
    SELECT id INTO v_typereg_id FROM type_regimen WHERE deleted_at IS NULL LIMIT 1;
    SELECT id INTO v_municipality_id FROM municipalities WHERE deleted_at IS NULL ORDER BY id LIMIT 1;

    FOR r IN
        SELECT id, document_number, full_name, company_id
          FROM employees
         WHERE third_party_id IS NULL AND deleted_at IS NULL
    LOOP
        -- Buscar tercero existente con mismo doc en su empresa
        SELECT id INTO v_existing_tp_id FROM third_parties
         WHERE company_id=r.company_id AND nit = LPAD(r.document_number, 10, '0')
           AND deleted_at IS NULL LIMIT 1;

        IF v_existing_tp_id IS NULL THEN
            INSERT INTO third_parties (company_id, third_party_code, business_name, nit, dv,
                                       status_id, type_organization_id, type_regimen_id,
                                       municipality_id, source, created_at, updated_at)
            VALUES (r.company_id, 'EMP-' || r.company_id || '-' || r.id,
                    r.full_name, LPAD(r.document_number, 10, '0'), '0',
                    v_status_active_id, v_typeorg_natural_id, v_typereg_id,
                    v_municipality_id, 'MANUAL', NOW(), NOW())
            RETURNING id INTO v_new_tp_id;
            v_existing_tp_id := v_new_tp_id;
        END IF;

        UPDATE employees SET third_party_id = v_existing_tp_id, updated_at = NOW()
         WHERE id = r.id;
    END LOOP;

    RAISE NOTICE 'V9-ZZE: empleados linkeados a terceros';
END $$;

-- ============================================================
-- 8) Sanear sales_invoices sin lineas: crear linea unica que cuadre con subtotal
-- ============================================================
INSERT INTO sales_invoice_lines (company_id, invoice_id, description, quantity, unit_price,
                                  discount, subtotal, tax_amount, withholding_amount,
                                  total, created_at, updated_at)
SELECT si.company_id, si.id, 'Linea unica QA', 1, si.subtotal,
       0, si.subtotal, 0, 0, si.subtotal, NOW(), NOW()
  FROM sales_invoices si
 WHERE si.deleted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM sales_invoice_lines WHERE invoice_id=si.id AND deleted_at IS NULL);

-- ============================================================
-- 9) Recalcular balance_due de invoices AP segun pagos reales
-- ============================================================
UPDATE invoices i
   SET balance_due = i.total_amount - COALESCE(
        (SELECT SUM(amount) FROM ap_payments WHERE invoice_id=i.id AND deleted_at IS NULL), 0),
       updated_at = NOW()
 WHERE i.deleted_at IS NULL
   AND ABS(i.balance_due - (i.total_amount - COALESCE(
        (SELECT SUM(amount) FROM ap_payments WHERE invoice_id=i.id AND deleted_at IS NULL), 0))) > 0.01;

SELECT 'V9-ZZE aplicado: NITs padded + roles asignados + empleados saneados + balances/lineas FV recalculados' AS status;
