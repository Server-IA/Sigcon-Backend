-- V31: Elimina la restriccion unica global sobre invoices.resolution_invoice.
--
-- Contexto: la resolucion DIAN autoriza a un proveedor a emitir un rango
-- consecutivo de facturas. Varias facturas del mismo proveedor (y entre
-- distintos proveedores) pueden compartir el mismo numero de resolucion,
-- por lo que una restriccion UNIQUE global sobre esta columna es
-- contablemente incorrecta.
--
-- La unicidad real de una factura ya esta cubierta por AP-01 E2:
-- (supplier_invoice_number, third_party_id, YEAR(invoice_date)).

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'uk_invoices_res_invoice_v2'
    ) THEN
        EXECUTE 'DROP INDEX public.uk_invoices_res_invoice_v2';
    END IF;
END $$;
