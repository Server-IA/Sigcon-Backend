DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT table_name, column_name
        FROM information_schema.columns
        WHERE column_name IN ('created_at', 'updated_at')
          AND table_schema = 'public'
    LOOP
        EXECUTE format(
            'ALTER TABLE %I ALTER COLUMN %I SET DEFAULT NOW();',
            r.table_name,
            r.column_name
        );
    END LOOP;
END $$;