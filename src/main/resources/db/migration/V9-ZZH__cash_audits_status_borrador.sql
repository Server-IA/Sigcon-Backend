-- HU-BNK-046 / HU-BNK-047 / HU-BNK-048 - 2026-04-25
-- Amplia el CHECK constraint de cash_audits.status para aceptar los nuevos
-- estados del ciclo de vida (BORRADOR / EN_REVISION / APROBADO / RECHAZADO /
-- ANULADO) sin perder compatibilidad con datos historicos (ABIERTO / CERRADO).
--
-- El CHECK previo (Hibernate auto-generado a partir del enum antiguo) solo
-- permitia ABIERTO/EN_REVISION/APROBADO/CERRADO. Tras agregar BORRADOR como
-- estado inicial, los INSERT fallaban con violacion de restriccion.
--
-- Idempotente: detecta el constraint existente por nombre dinamico y lo
-- reemplaza por uno permisivo para los 7 valores validos del enum vigente.

DO $$
DECLARE
    constraint_name_var TEXT;
BEGIN
    SELECT con.conname
      INTO constraint_name_var
      FROM pg_constraint con
      JOIN pg_class cls ON cls.oid = con.conrelid
     WHERE cls.relname = 'cash_audits'
       AND con.contype = 'c'
       AND pg_get_constraintdef(con.oid) ILIKE '%status%'
     LIMIT 1;

    IF constraint_name_var IS NOT NULL THEN
        EXECUTE 'ALTER TABLE cash_audits DROP CONSTRAINT ' || quote_ident(constraint_name_var);
    END IF;

    ALTER TABLE cash_audits
        ADD CONSTRAINT cash_audits_status_check
        CHECK (status IN ('BORRADOR','EN_REVISION','APROBADO','RECHAZADO','ANULADO','ABIERTO','CERRADO'));
END $$;

-- Normalizar datos historicos: ABIERTO se reinterpreta como BORRADOR
-- (mismo significado funcional segun HU-BNK-046).
UPDATE cash_audits
   SET status = 'BORRADOR'
 WHERE status = 'ABIERTO';
