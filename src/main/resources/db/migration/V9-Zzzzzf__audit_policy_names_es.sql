-- ============================================================================
-- V9-Zzzzzf : QA Bloque AU (2026-05-25)
-- Corrige los nombres/descripciones de las 4 politicas de retencion semilla
-- para que aparezcan en espanol correcto ("10 anios" -> "10 anos" con tilde).
--
-- Contexto: V9-B sembraba "Logs CRITICOS - 10 anios" (sin tildes, para evitar
-- problemas de charset del loader). Ahora V9-B ya inserta los nombres en espanol;
-- esta migracion corrige las filas YA existentes (todas las empresas, incluidas
-- las clonadas por V9-ZZG desde la empresa 1) y deduplica si V9-B re-inserto la
-- version nueva antes de que esta migracion corriera.
--
-- Las tildes se construyen con chr() (i=237, o=243, n-tilde=241) para no depender
-- del charset con que DataInitializer lea este archivo.
-- Idempotente: tras correr, ya no quedan filas con el nombre viejo, asi que en
-- arranques posteriores los WHERE no afectan ninguna fila.
-- ============================================================================

DO $$
DECLARE
    old_names  TEXT[] := ARRAY[
        'Logs CRITICOS - 10 anios',
        'Logs ALTO - 5 anios',
        'Logs MEDIO/BAJO - 2 anios',
        'Logs BAJO - 1 anio'
    ];
    new_names  TEXT[] := ARRAY[
        'Logs cr' || chr(237) || 'ticos - 10 a' || chr(241) || 'os',
        'Logs severidad alta - 5 a' || chr(241) || 'os',
        'Logs severidad media/baja - 2 a' || chr(241) || 'os',
        'Logs severidad baja - 1 a' || chr(241) || 'o'
    ];
    new_descs  TEXT[] := ARRAY[
        'Eventos de severidad cr' || chr(237) || 'tica se retienen 10 a' || chr(241) || 'os (Decreto 2649/1993 Art. 134)',
        'Eventos de severidad alta se retienen 5 a' || chr(241) || 'os (Estatuto Tributario)',
        'Eventos de severidad media y baja se retienen 2 a' || chr(241) || 'os',
        'Eventos de severidad baja (inicio de sesi' || chr(243) || 'n/vista/exportaci' || chr(243) || 'n) se retienen 1 a' || chr(241) || 'o'
    ];
    i INT;
BEGIN
    FOR i IN 1 .. array_length(old_names, 1) LOOP
        -- 1) Dedup: si en una empresa ya existe la version nueva (porque V9-B
        --    la re-inserto antes que esta migracion), borrar la fila vieja.
        DELETE FROM audit_retention_policies o
         USING audit_retention_policies n
         WHERE o.name = old_names[i]
           AND n.name = new_names[i]
           AND o.company_id IS NOT DISTINCT FROM n.company_id;

        -- 2) Renombrar las filas viejas restantes (empresas que solo tenian la vieja).
        UPDATE audit_retention_policies
           SET name        = new_names[i],
               description = new_descs[i],
               updated_at  = NOW()
         WHERE name = old_names[i];
    END LOOP;

    RAISE NOTICE 'V9-Zzzzzf: politicas de retencion normalizadas a espanol (anios -> anos con tilde).';
END $$;

-- Catch-all defensivo: cualquier politica que aun contenga "anio" (variantes o
-- clones tardios) recibe el fix puntual anios->anos / anio->ano.
UPDATE audit_retention_policies
   SET name        = REPLACE(name, 'anio', 'a' || chr(241) || 'o'),
       description  = REPLACE(COALESCE(description, ''), 'anio', 'a' || chr(241) || 'o'),
       updated_at   = NOW()
 WHERE name LIKE '%anio%' OR COALESCE(description, '') LIKE '%anio%';
