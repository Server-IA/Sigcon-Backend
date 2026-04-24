-- =============================================================================
-- V9-Z6: Alinea company_id de tablas child con su parent tercero/entidad.
--
-- Causa del problema: seeds legacy (antes de V9-Z) insertaban filas en tablas
-- junction/history con `DEFAULT 1` para company_id, aunque el parent tercero
-- estuviera en otra empresa. Cuando @PostLoad en la entidad child compara
-- company_id != TenantContext actual, lanza TenantIsolationException y el
-- GlobalExceptionHandler responde HTTP 404 "Recurso no encontrado", impidiendo
-- que usuarios tenant vean SUS datos.
--
-- Este script corre en idempotente: solo afecta filas con inconsistencia.
-- =============================================================================

-- 1. third_party_withholding_assignments
UPDATE third_party_withholding_assignments wa
   SET company_id = tp.company_id
  FROM third_parties tp
 WHERE wa.third_party_id = tp.id
   AND wa.company_id <> tp.company_id
   AND wa.deleted_at IS NULL;

-- 2. third_party_role_assignments_v2 (si existe)
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_name='third_party_role_assignments_v2' AND column_name='company_id') THEN
    UPDATE third_party_role_assignments_v2 ra
       SET company_id = tp.company_id
      FROM third_parties tp
     WHERE ra.third_party_id = tp.id
       AND ra.company_id <> tp.company_id
       AND ra.deleted_at IS NULL;
  END IF;
END $$;

-- 3. third_contacts
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_name='third_contacts' AND column_name='company_id') THEN
    UPDATE third_contacts c
       SET company_id = tp.company_id
      FROM third_parties tp
     WHERE c.third_party_id = tp.id
       AND c.company_id <> tp.company_id
       AND c.deleted_at IS NULL;
  END IF;
END $$;

-- 4. commercial_data (FK column: third_party_id o similar)
DO $$
DECLARE v_fk VARCHAR;
BEGIN
  SELECT column_name INTO v_fk FROM information_schema.columns
   WHERE table_name='commercial_data' AND column_name IN ('third_party_id','third_party_client_id','client_id')
   LIMIT 1;
  IF v_fk IS NOT NULL THEN
    EXECUTE format('UPDATE commercial_data cd SET company_id = tp.company_id '
                   'FROM third_parties tp WHERE cd.%I = tp.id '
                   'AND cd.company_id <> tp.company_id AND cd.deleted_at IS NULL', v_fk);
  END IF;
END $$;

-- 5. third_party_bank_accounts
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_name='third_party_bank_accounts' AND column_name='company_id') THEN
    UPDATE third_party_bank_accounts b
       SET company_id = tp.company_id
      FROM third_parties tp
     WHERE b.third_party_id = tp.id
       AND b.company_id <> tp.company_id
       AND b.deleted_at IS NULL;
  END IF;
END $$;

-- 6. ecl_segmentation
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_name='ecl_segmentation' AND column_name='company_id') THEN
    UPDATE ecl_segmentation e
       SET company_id = tp.company_id
      FROM third_parties tp
     WHERE e.client_id = tp.id
       AND e.company_id <> tp.company_id
       AND e.deleted_at IS NULL;
  END IF;
END $$;

-- 7. ecl_segmentation_history (puede o no tener deleted_at)
DO $$
DECLARE v_has_deleted BOOLEAN;
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_name='ecl_segmentation_history' AND column_name='company_id') THEN
    SELECT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_name='ecl_segmentation_history' AND column_name='deleted_at')
      INTO v_has_deleted;
    IF v_has_deleted THEN
      UPDATE ecl_segmentation_history h
         SET company_id = tp.company_id
        FROM third_parties tp
       WHERE h.client_id = tp.id
         AND h.company_id <> tp.company_id
         AND h.deleted_at IS NULL;
    ELSE
      UPDATE ecl_segmentation_history h
         SET company_id = tp.company_id
        FROM third_parties tp
       WHERE h.client_id = tp.id
         AND h.company_id <> tp.company_id;
    END IF;
  END IF;
END $$;

-- 8. third_party_change_history (no tiene deleted_at)
DO $$
DECLARE v_has_deleted BOOLEAN;
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_name='third_party_change_history' AND column_name='company_id') THEN
    SELECT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_name='third_party_change_history' AND column_name='deleted_at')
      INTO v_has_deleted;
    IF v_has_deleted THEN
      UPDATE third_party_change_history h
         SET company_id = tp.company_id
        FROM third_parties tp
       WHERE h.third_party_id = tp.id
         AND h.company_id <> tp.company_id
         AND h.deleted_at IS NULL;
    ELSE
      UPDATE third_party_change_history h
         SET company_id = tp.company_id
        FROM third_parties tp
       WHERE h.third_party_id = tp.id
         AND h.company_id <> tp.company_id;
    END IF;
  END IF;
END $$;

-- 9. commercial_data_history (puede o no tener deleted_at)
DO $$
DECLARE v_has_deleted BOOLEAN;
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_name='commercial_data_history' AND column_name='company_id') THEN
    SELECT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_name='commercial_data_history' AND column_name='deleted_at')
      INTO v_has_deleted;
    IF v_has_deleted THEN
      UPDATE commercial_data_history h
         SET company_id = cd.company_id
        FROM commercial_data cd
       WHERE h.commercial_data_id = cd.id
         AND h.company_id <> cd.company_id
         AND h.deleted_at IS NULL;
    ELSE
      UPDATE commercial_data_history h
         SET company_id = cd.company_id
        FROM commercial_data cd
       WHERE h.commercial_data_id = cd.id
         AND h.company_id <> cd.company_id;
    END IF;
  END IF;
END $$;

SELECT 'V9-Z6 ejecutado: company_id alineado en child tables' AS status;
