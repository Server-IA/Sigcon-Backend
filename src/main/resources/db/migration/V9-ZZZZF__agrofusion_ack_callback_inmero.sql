-- V9-ZZZZF (2026-05-09): AgroFusion confirmo el endpoint real para ACKs
-- en su entorno de prueba: https://api.inmero.co/agrofusion/test/int/accounting-ACK
--
-- Contexto:
--   AgroFusion comunico via canal QA (mensaje 09:54 p.m.) que el callback
--   de confirmacion de ACK debe apuntar a esa URL especifica de su
--   entorno de pruebas. Antes apuntabamos a placeholders genericos como
--   https://api.agrofusion.co/integrations/aaef/ack que NO existen.
--
-- Comportamiento idempotente:
--   - Solo actualiza filas cuyo valor actual NO sea ya el endpoint correcto
--     (asi que se puede re-ejecutar sin efectos colaterales).
--   - NO toca filas con localhost o el mock (LocalAaefMockOverrides las
--     reescribe en cada arranque cuando SIGCON_INTEGRATION_MOCKS_ENABLED=true).
--
-- Si el equipo de AgroFusion cambia el endpoint en el futuro, crear nueva
-- migracion en lugar de modificar esta (preservar trazabilidad).

UPDATE parameters
   SET value = 'https://api.inmero.co/agrofusion/test/int/accounting-ACK',
       updated_at = NOW()
 WHERE name = 'AGROFUSION_ACK_CALLBACK_URL'
   AND deleted_at IS NULL
   AND value NOT LIKE '%localhost%'
   AND value NOT LIKE '%mock%'
   AND value <> 'https://api.inmero.co/agrofusion/test/int/accounting-ACK';
