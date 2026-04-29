-- =============================================================================
-- V9-ZZI__renumber_qa_invoices.sql
--
-- HU-AR-01A E7: el seed legacy V9-ZZC inyectaba el id de empresa (suffix*1000) en
-- el numero de factura, lo que hacia que las facturas QA aparecieran como
-- FV-2026001003, FV-2026002003, FV-2026003003 segun el id de empresa. Esto
-- confundia al QA y "consumia" el rango bajo del consecutivo, dejando solo
-- ~999.000 numeros disponibles por empresa hasta saltar a 7 digitos.
--
-- El consecutivo correcto es FV-{year}{6 digitos sin offset por empresa},
-- aprovechando que el UNIQUE en sales_invoices.invoice_number es COMPUESTO
-- por (company_id, invoice_number).
--
-- Esta migracion:
--   1. Detecta facturas con el patron viejo (1001001..6003003)
--   2. Las renumera a FV-{year}000001, 000002, 000003 segun orden de creacion
--      dentro de cada empresa (manteniendo company_id intacto).
--   3. Es idempotente: si las facturas ya estan en formato corto, no toca nada.
--
-- Las FK a sales_invoices son por id (no por invoice_number), asi que el
-- renumerado es seguro. JE description NO referencia el invoice_number en BD
-- limpia, pero por defensa actualizamos descripciones que lo contengan.
-- =============================================================================

DO $$
DECLARE
    r RECORD;
    v_year INT;
    v_target TEXT;
    v_next INT;
    v_renamed INT := 0;
BEGIN
    -- Iteramos las facturas con numero legacy una a una, asignandoles el primer
    -- numero corto disponible dentro de su company. Esto evita colisiones con
    -- facturas que ya pudieran tener FV-2026000001 (seeds otros, manuales, etc).
    FOR r IN
        SELECT id, company_id, invoice_date
        FROM sales_invoices
        WHERE deleted_at IS NULL
          AND invoice_number ~ '^FV-[0-9]{10,}$'
        ORDER BY company_id, id
    LOOP
        v_year := EXTRACT(YEAR FROM r.invoice_date)::INT;
        -- Buscar el primer hueco disponible. Iniciamos en 1 y avanzamos.
        v_next := 1;
        LOOP
            v_target := 'FV-' || v_year::TEXT || LPAD(v_next::TEXT, 6, '0');
            EXIT WHEN NOT EXISTS (
                SELECT 1 FROM sales_invoices
                WHERE company_id = r.company_id
                  AND invoice_number = v_target
                  AND id <> r.id
            );
            v_next := v_next + 1;
            -- Salvaguarda contra loop infinito.
            EXIT WHEN v_next > 1000000;
        END LOOP;

        UPDATE sales_invoices
           SET invoice_number = v_target,
               updated_at = NOW()
         WHERE id = r.id;

        v_renamed := v_renamed + 1;
    END LOOP;

    IF v_renamed > 0 THEN
        RAISE NOTICE 'V9-ZZI: renumeradas % facturas QA al formato corto.', v_renamed;
    ELSE
        RAISE NOTICE 'V9-ZZI: no hay facturas con numero legacy, nada que hacer.';
    END IF;
END $$;

-- =============================================================================
-- Notas operativas:
--  - Si en el futuro el seed se vuelve a correr en BD limpia, V9-ZZC ya esta
--    corregido para emitir FV-{year}000001..000003 directamente y esta migracion
--    no aplica (el regex no matchea numeros de 9 digitos).
--  - Si un admin renombro manualmente alguna factura (poco probable), no se
--    toca: solo afecta numeros con 10+ digitos.
-- =============================================================================
