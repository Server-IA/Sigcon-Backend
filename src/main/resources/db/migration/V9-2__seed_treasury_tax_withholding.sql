-- Semilla: formas de pago, retenciones (catálogo), cuentas auxiliares PUC para impuestos,
-- reglas ruler_tax + tax_ruler_accounts, banco y cuenta bancaria demo, caja demo.
-- Requisitos: empresa en companies, PUC (V9), moneda COP, tercero empleado demo (V3 third_parties),
-- y tablas creadas (Flyway + JPA). Los INSERT a banks/bank_accounts/cash solo aplican si la tabla existe.

-- ---------------------------------------------------------------------------
-- Formas de pago (además de Contado / Crédito en V3-1)
-- ---------------------------------------------------------------------------
-- INSERT INTO payment_forms (name, code, description, created_at, updated_at)
-- SELECT * FROM (
--     VALUES
--     ('Transferencia bancaria', 'TRANSFER', 'Pago por transferencia ACH / consignación', NOW(), NOW()),
--     ('Cheque', 'CHECK', 'Pago con cheque', NOW(), NOW()),
--     ('Tarjeta débito', 'DEBIT_CARD', 'Pago con tarjeta débito', NOW(), NOW()),
--     ('Tarjeta crédito', 'CREDIT_CARD', 'Pago con tarjeta crédito', NOW(), NOW()),
--     ('Medios electrónicos', 'ELECTRONIC', 'PSE, billeteras u otros medios digitales', NOW(), NOW())
-- ) AS v (name, code, description, created_at, updated_at)
-- WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'payment_forms')
--   AND NOT EXISTS (
--       SELECT 1 FROM payment_forms pf
--       WHERE UPPER(TRIM(pf.code)) = UPPER(TRIM(v.code)) AND pf.deleted_at IS NULL
--   );

-- ---------------------------------------------------------------------------
-- Catálogo de retenciones (ampliación; base RETEIVA/RETEICA/RETEFUENTE en V3)
-- ---------------------------------------------------------------------------
-- INSERT INTO withholdings (name, code, created_at, updated_at)
-- SELECT v.name, v.code, NOW(), NOW()
-- FROM (VALUES
--     ('Retención ICA municipio', 'RETE_ICA'),
--     ('Autorretención de renta', 'AUTORRETE_RENTA'),
--     ('Retención en la fuente practicada servicios', 'RTEFTE_SERVICIOS'),
--     ('Retención en la fuente practicada compras', 'RTEFTE_COMPRAS'),
--     ('Retención de IVA practicada', 'RETEIVA_PRACT')
-- ) AS v (name, code)
-- WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'withholdings')
--   AND NOT EXISTS (
--       SELECT 1 FROM withholdings w
--       WHERE UPPER(TRIM(w.code)) = UPPER(TRIM(v.code)) AND w.deleted_at IS NULL
--   );

-- ---------------------------------------------------------------------------
-- Cuentas contables (accounting_accounts) ligadas a PUC para tesorería e impuestos
-- ---------------------------------------------------------------------------
INSERT INTO accounting_accounts (
    puc_id, custom_name, currency_type_id, cost_center_id, tax_rule_id, nature, status, company_id,
    created_at, updated_at, created_by
)
SELECT p.id, v.custom_name, ct.id, NULL, NULL, v.nature::varchar, 'ACTIVE', c.id, NOW(), NOW(),
       (SELECT u.id FROM users u WHERE u.username = 'superadmin' AND u.deleted_at IS NULL LIMIT 1)
FROM (VALUES
    ('1105', 'Caja general operativa', 'DEBIT'),
    ('1110', 'Bancos', 'DEBIT'),
    -- ('135530', 'PUC — Impuestos descontables (IVA crédito)', 'DEBIT'),
    ('2408', 'Impuesto ventas por pagar', 'CREDIT'),
    ('2365', 'Retención fuente por pagar', 'CREDIT'),
    ('2367', 'Impuesto ventas retenido por pagar', 'CREDIT'),
    ('2368', 'Impuesto industria y comercio retenido por pagar', 'CREDIT'),
    ('1528', 'Equipo de computo','DEBIT'),
    ('2205', 'Proveedores', 'DEBIT')
) AS v (puc_code, custom_name, nature)
JOIN cfg_chart_of_accounts p ON p.account_code = v.puc_code AND p.deleted_at IS NULL
JOIN cfg_currency_types ct ON ct.iso_code = 'COP' AND ct.deleted_at IS NULL
CROSS JOIN (SELECT co.id FROM companies co WHERE co.deleted_at IS NULL ORDER BY co.id LIMIT 1) c
WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'accounting_accounts')
  AND EXISTS (SELECT 1 FROM companies com WHERE com.deleted_at IS NULL)
  AND NOT EXISTS (
      SELECT 1 FROM accounting_accounts a
      WHERE a.company_id = c.id AND a.custom_name = v.custom_name AND a.deleted_at IS NULL
  );