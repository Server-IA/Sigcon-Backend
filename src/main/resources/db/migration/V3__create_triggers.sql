-- Tasas de cambio
CREATE EXTENSION IF NOT EXISTS btree_gist;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'no_overlapping_exchange_rates'
    ) THEN
        ALTER TABLE exchange_rates
        ADD CONSTRAINT no_overlapping_exchange_rates
        EXCLUDE USING gist (
            currency_id WITH =,
            currency_iso WITH =,
            company_id WITH =,
            exchange_type WITH =,
            daterange(start_date, end_date, '[]') WITH &&
        )
        WHERE (deleted_at IS NULL);
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION check_min_role1_user()
RETURNS TRIGGER AS $$
DECLARE
    total_role1 INTEGER;
BEGIN
    -- Solo validar si el usuario afectado tiene role_id = 1
    IF OLD.role_id = 1 THEN
        
        SELECT COUNT(*) INTO total_role1
        FROM users_roles
        WHERE role_id = 1;

        -- Si solo queda 1, impedir operación
        IF total_role1 <= 1 THEN
            RAISE EXCEPTION 
                USING 
                    MESSAGE = 'Debe existir al menos un usuario con SUPERADMIN',
                    ERRCODE = '45000'; -- código personalizado
        END IF;
    END IF;

    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER prevent_delete_last_role1
BEFORE DELETE ON users_roles
FOR EACH ROW
EXECUTE FUNCTION check_min_role1_user();

CREATE TRIGGER prevent_update_last_role1
BEFORE UPDATE ON users_roles
FOR EACH ROW
WHEN (OLD.role_id = 1 AND NEW.role_id <> 1)
EXECUTE FUNCTION check_min_role1_user();