-- HU-ACT-01 E1 / E8 / E9 (QA 2026-05-05)
--
-- El constraint `chk_origin_payment_not_null` (creado en V3-1__invoices.sql)
-- exigia que TODO voucher de compra de activo tuviera al menos uno de
-- bank_account_id, cash_account_id o check_id. Eso bloqueaba la HU-ACT-01 E1
-- "compra a credito" porque en credito NO hay salida inmediata de bancos/caja
-- (la CxP en AP cubre la deuda). El service `VoucherService.createVoucher`
-- ya valida correctamente: solo exige origen cuando `paymentForm.isContado=true`.
-- Aqui DROP el CHECK de BD para no chocar con flujos legitimos de credito.
--
-- Idempotente: si ya fue removido, el DROP IF EXISTS no falla.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'chk_origin_payment_not_null'
           AND conrelid = 'vouchers'::regclass
    ) THEN
        ALTER TABLE vouchers DROP CONSTRAINT chk_origin_payment_not_null;
        RAISE NOTICE 'Constraint chk_origin_payment_not_null removido (HU-ACT-01 E1 credito)';
    END IF;
END $$;
