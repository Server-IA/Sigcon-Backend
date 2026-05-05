-- LLaves para unicidad

DROP INDEX IF EXISTS uk_payment_methods_code;

DO $$
BEGIN
    -- Nota: el indice global uk_invoices_res_invoice_v2 sobre resolution_invoice
    -- fue eliminado (era contablemente incorrecto: una resolucion DIAN autoriza
    -- un rango de facturas a un proveedor, por lo que varias facturas comparten
    -- la misma resolucion). La unicidad real esta cubierta por AP-01 E2:
    -- (supplier_invoice_number, third_party_id, YEAR(invoice_date)).
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'invoices' AND column_name = 'company_id') THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_invoices_res_type
            ON invoices (type_invoice_id, resolution, company_id) WHERE deleted_at IS NULL;
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS uk_invoices_res_type_v2
            ON invoices (type_invoice_id, resolution) WHERE deleted_at IS NULL;
    END IF;

    -- Dropear el indice antiguo si todavia existe por startups previos
    DROP INDEX IF EXISTS uk_invoices_res_invoice_v2;
    DROP INDEX IF EXISTS uk_invoices_res_invoice_company;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'invoices' AND column_name = 'location_origin_id') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_origin_destination_different') THEN
            ALTER TABLE invoices
            ADD CONSTRAINT chk_origin_destination_different
            CHECK (
                location_origin_id IS NULL
                OR location_destination_id IS NULL
                OR location_origin_id <> location_destination_id
            );
        END IF;
    END IF;
END$$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_types_invoices_code
ON types_invoices (name, code)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_invoice_states_name_code_block
ON invoice_states (name, code, block)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_forms_code
ON payment_forms (code)
WHERE deleted_at IS NULL;

-- Migraciones

INSERT INTO types_invoices (name, code, description, created_at, updated_at)
SELECT * FROM (
    VALUES
    ('Factura de compra', 'FC', 'Factura de compra', now(), now()),
    ('Transferencia de inventario', 'TI', 'Transferencia de inventario', now(), now()),
    ('Recepcion de inventario', 'RI', 'Recepcion de inventario', now(), now()),
     
    ('Factura de venta', 'FV', 'Factura de venta', now(), now()),
    ('Nota de crédito', 'NC', 'Nota de crédito', now(), now()),
    ('Nota de débito', 'ND', 'Nota de débito', now(), now())
) AS v (name, code, description, created_at, updated_at)
WHERE NOT EXISTS (
    SELECT 1 FROM types_invoices WHERE (name = v.name OR code = v.code) AND deleted_at IS NULL
);

INSERT INTO invoice_states (name, code, block, description, created_at, updated_at)
SELECT * FROM (
    VALUES

    ('En proceso', 'PEND', 'PURCHASE', 'Compra en proceso', now(), now()),
    ('Finalizada', 'FIN', 'PURCHASE', 'Compra finalizada', now(), now()),
    ('Cancelada', 'CAN', 'PURCHASE', 'Compra cancelada', now(), now()),

    ('En proceso', 'PEND', 'TRANSFER', 'Transferencia en processo', now(), now()),
    ('Finalizada', 'FIN', 'TRANSFER', 'Transferencia finalizada', now(), now()),
    ('Cancelada', 'CAN', 'TRANSFER', 'Transferencia cancelada', now(), now())
) AS v (name, code, block, description, created_at, updated_at)
WHERE NOT EXISTS (
    SELECT 1 FROM invoice_states WHERE (name = v.name AND code = v.code AND block = v.block) AND deleted_at IS NULL
);

INSERT INTO payment_forms (name, code, description, created_at, updated_at)
SELECT * FROM (
    VALUES
    ('Contado', 'CASH', 'Contado', now(), now()),
    ('Credito', 'CREDIT', 'Credito', now(), now())
) AS v (name, code, description, created_at, updated_at)
WHERE NOT EXISTS (
    SELECT 1 FROM payment_forms WHERE (name = v.name OR code = v.code) AND deleted_at IS NULL
);

-- Tipos de comprobantes

-- DROP UNIQUE INDEX IF EXISTS uk_voucher_types_code;

CREATE UNIQUE INDEX IF NOT EXISTS uk_voucher_types_code
ON voucher_types (code)
WHERE deleted_at IS NULL;

INSERT INTO voucher_types (name, code, description, created_at, updated_at)
SELECT * FROM (
    VALUES
    ('Pago de compra', 'PC', 'Pago de compra', now(), now()),
    ('Comprobante manual', 'CM', 'Comprobante manual', now(), now())
) AS v (name, code, description, created_at, updated_at)
WHERE NOT EXISTS (
    SELECT 1 FROM voucher_types WHERE (name = v.name OR code = v.code) AND deleted_at IS NULL
);

-- comprobantes

DROP INDEX IF EXISTS uk_vouchers_number;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'vouchers' AND column_name = 'company_id') THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_vouchers_number
            ON vouchers (number, voucher_type_id, company_id) WHERE deleted_at IS NULL;
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS uk_vouchers_number_v2
            ON vouchers (number, voucher_type_id) WHERE deleted_at IS NULL;
    END IF;
END $$;

-- QA-BLOQUE-AN (2026-04-29): CHECKs creados solo si no existen, en lugar de
-- DROP+ADD en cada arranque. Asi el script es idempotente real y no machaca
-- modificaciones manuales.
DO $$
BEGIN
    -- HU-ACT-01 E1 (QA 2026-05-05): constraint bloqueaba compras a credito.
    -- La validacion equivalente esta en VoucherService.createVoucher (solo
    -- exige origen cuando paymentForm.isContado=true). NO recrear el CHECK.
    IF FALSE AND NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'chk_origin_payment_not_null'
           AND conrelid = 'vouchers'::regclass
    ) THEN
        ALTER TABLE vouchers
        ADD CONSTRAINT chk_origin_payment_not_null
        CHECK (
            bank_account_id IS NOT NULL
            OR cash_account_id IS NOT NULL
            OR check_id IS NOT NULL
        );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'chk_amount_positive'
           AND conrelid = 'vouchers'::regclass
    ) THEN
        ALTER TABLE vouchers
        ADD CONSTRAINT chk_amount_positive
        CHECK (amount IS NOT NULL AND amount > 0);
    END IF;
END $$;
