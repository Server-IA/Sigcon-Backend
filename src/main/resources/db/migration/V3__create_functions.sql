CREATE OR REPLACE FUNCTION check_min_role1_user()
RETURNS TRIGGER AS $$
DECLARE
    total_role1 INTEGER;
BEGIN
    -- Solo validar si el usuario afectado tiene role_id = 1
    IF OLD.role_id = 1 THEN
        
        SELECT COUNT(*) INTO total_role1
        FROM users
        WHERE role_id = 1;

        -- Si solo queda 1, impedir operación
        IF total_role1 <= 1 THEN
            RAISE EXCEPTION 'Debe existir al menos un usuario con SUPERADMIN';
        END IF;
    END IF;

    RETURN OLD;
END;


$$ LANGUAGE plpgsql;