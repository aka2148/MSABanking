CREATE TEMP TABLE _bankingtech_fd_accrual_fk_backup AS
SELECT
    n.nspname AS table_schema,
    c.relname AS table_name,
    con.conname AS constraint_name,
    pg_get_constraintdef(con.oid) AS constraint_definition
FROM pg_constraint con
JOIN pg_class c ON c.oid = con.conrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE con.contype = 'f'
  AND n.nspname = 'public'
  AND c.relname = 'fd_interest_accrual';

DO $$
DECLARE
    foreign_key RECORD;
BEGIN
    FOR foreign_key IN SELECT * FROM _bankingtech_fd_accrual_fk_backup LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I DROP CONSTRAINT %I',
            foreign_key.table_schema,
            foreign_key.table_name,
            foreign_key.constraint_name
        );
    END LOOP;
END $$;

ALTER TABLE public.fd_interest_accrual
    ALTER COLUMN fia_accrual_id TYPE BIGINT,
    ALTER COLUMN fia_account_id TYPE BIGINT,
    ALTER COLUMN fia_currency_id TYPE BIGINT;

DO $$
DECLARE
    foreign_key RECORD;
BEGIN
    FOR foreign_key IN SELECT * FROM _bankingtech_fd_accrual_fk_backup LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I ADD CONSTRAINT %I %s',
            foreign_key.table_schema,
            foreign_key.table_name,
            foreign_key.constraint_name,
            foreign_key.constraint_definition
        );
    END LOOP;
END $$;

DROP TABLE _bankingtech_fd_accrual_fk_backup;
