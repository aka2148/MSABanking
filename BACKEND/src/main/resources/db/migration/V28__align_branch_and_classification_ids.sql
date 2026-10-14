-- Complete the existing Java Long identifier alignment without changing V1-V27.
-- PostgreSQL will not change the type of a referenced key while dependent
-- foreign-key constraints remain installed. Save and restore every FK that
-- references either key so this also works on the existing public schema.
CREATE TEMP TABLE _bankingtech_branch_classification_fk_backup AS
SELECT
    n.nspname AS table_schema,
    c.relname AS table_name,
    con.conname AS constraint_name,
    pg_get_constraintdef(con.oid) AS constraint_definition
FROM pg_constraint con
JOIN pg_class c ON c.oid = con.conrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE con.contype = 'f'
  AND con.confrelid IN (
      'public.branches'::regclass,
      'public.customer_classification_types'::regclass
  );

DO $$
DECLARE
    foreign_key RECORD;
BEGIN
    FOR foreign_key IN SELECT * FROM _bankingtech_branch_classification_fk_backup LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I DROP CONSTRAINT %I',
            foreign_key.table_schema,
            foreign_key.table_name,
            foreign_key.constraint_name
        );
    END LOOP;
END $$;

ALTER TABLE public.branches ALTER COLUMN bra_branch_id TYPE BIGINT;
ALTER TABLE public.customer_classification_types ALTER COLUMN cct_type_id TYPE BIGINT;

DO $$
DECLARE
    foreign_key RECORD;
BEGIN
    FOR foreign_key IN SELECT * FROM _bankingtech_branch_classification_fk_backup LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I ADD CONSTRAINT %I %s',
            foreign_key.table_schema,
            foreign_key.table_name,
            foreign_key.constraint_name,
            foreign_key.constraint_definition
        );
    END LOOP;
END $$;

DROP TABLE _bankingtech_branch_classification_fk_backup;
