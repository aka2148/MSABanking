DO $$
DECLARE
    version_column RECORD;
BEGIN
    FOR version_column IN
        SELECT table_schema, table_name, column_name
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND column_name LIKE '%_version'
          AND data_type IN ('smallint', 'integer', 'bigint', 'numeric')
    LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I ALTER COLUMN %I TYPE VARCHAR(20) USING %I::TEXT',
            version_column.table_schema,
            version_column.table_name,
            version_column.column_name,
            version_column.column_name
        );
    END LOOP;
END $$;
