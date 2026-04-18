-- V9-P: migrar las columnas temporales de integration_batches a TIMESTAMPTZ.
--
-- Motivo: actualmente el container corre en UTC y las columnas son
-- `timestamp without time zone`, asi que un contador en Bogota ve
-- received_at='2026-04-18 18:07' cuando el lote llego a las 13:07
-- local. No hay informacion perdida (UTC - 5 = Bogota), pero la UX
-- es incomoda.
--
-- Solucion: cambiar a TIMESTAMPTZ. Postgres guarda internamente en
-- UTC (igual que ahora) pero convierte al TZ del cliente al mostrar.
-- Cualquier SQL client con SET TIME ZONE='America/Bogota' vera hora
-- local, y el driver JDBC de Postgres sigue hablando con Java sin
-- cambios de codigo (LocalDateTime -> timestamptz usa JVM TZ=UTC).
--
-- Idempotente: si la columna ya es timestamptz, el ALTER es no-op.
-- La clausula USING ... AT TIME ZONE 'UTC' le dice a Postgres que
-- los valores existentes deben interpretarse como UTC (porque el
-- container siempre corrio en UTC).

ALTER TABLE integration_batches
    ALTER COLUMN received_at       TYPE TIMESTAMPTZ USING received_at       AT TIME ZONE 'UTC',
    ALTER COLUMN processed_at      TYPE TIMESTAMPTZ USING processed_at      AT TIME ZONE 'UTC',
    ALTER COLUMN ack_sent_at       TYPE TIMESTAMPTZ USING ack_sent_at       AT TIME ZONE 'UTC',
    ALTER COLUMN ack_next_retry_at TYPE TIMESTAMPTZ USING ack_next_retry_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at        TYPE TIMESTAMPTZ USING created_at        AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at        TYPE TIMESTAMPTZ USING updated_at        AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at        TYPE TIMESTAMPTZ USING deleted_at        AT TIME ZONE 'UTC';

-- Verificacion en logs
DO $$
BEGIN
    RAISE NOTICE 'V9-P: integration_batches columnas temporales -> TIMESTAMPTZ';
END $$;
