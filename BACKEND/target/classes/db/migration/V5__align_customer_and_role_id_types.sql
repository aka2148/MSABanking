CREATE TEMP TABLE _bankingtech_fk_backup_v5 AS
SELECT
    n.nspname AS table_schema,
    c.relname AS table_name,
    con.conname AS constraint_name,
    pg_get_constraintdef(con.oid) AS constraint_definition
FROM pg_constraint con
JOIN pg_class c ON c.oid = con.conrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
JOIN pg_class referenced_table ON referenced_table.oid = con.confrelid
JOIN pg_namespace referenced_schema ON referenced_schema.oid = referenced_table.relnamespace
WHERE con.contype = 'f'
  AND n.nspname = 'public'
  AND (
      c.relname IN (
          'users', 'roles', 'user_credentials', 'user_roles', 'customers',
          'customer_details', 'customer_addresses', 'customer_phone_numbers',
          'customer_emails'
      )
      OR referenced_schema.nspname = 'public'
         AND referenced_table.relname IN (
             'users', 'roles', 'user_credentials', 'user_roles', 'customers',
             'customer_details', 'customer_addresses', 'customer_phone_numbers',
             'customer_emails'
         )
  );

DO $$
DECLARE
    foreign_key RECORD;
BEGIN
    FOR foreign_key IN SELECT * FROM _bankingtech_fk_backup_v5 LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I DROP CONSTRAINT %I',
            foreign_key.table_schema,
            foreign_key.table_name,
            foreign_key.constraint_name
        );
    END LOOP;
END $$;

ALTER TABLE public.roles
    ALTER COLUMN rol_role_id TYPE BIGINT;
ALTER TABLE public.user_roles
    ALTER COLUMN ur_user_role_id TYPE BIGINT,
    ALTER COLUMN ur_user_id TYPE BIGINT,
    ALTER COLUMN ur_role_id TYPE BIGINT;
ALTER TABLE public.customer_details
    ALTER COLUMN cud_detail_id TYPE BIGINT,
    ALTER COLUMN cud_customer_id TYPE BIGINT;
ALTER TABLE public.customer_addresses
    ALTER COLUMN cad_customer_id TYPE BIGINT,
    ALTER COLUMN cad_verified_by TYPE BIGINT;
ALTER TABLE public.customer_phone_numbers
    ALTER COLUMN cph_phone_id TYPE BIGINT,
    ALTER COLUMN cph_customer_id TYPE BIGINT;
ALTER TABLE public.customer_emails
    ALTER COLUMN cem_email_id TYPE BIGINT,
    ALTER COLUMN cem_customer_id TYPE BIGINT;

DO $$
DECLARE
    foreign_key RECORD;
BEGIN
    FOR foreign_key IN SELECT * FROM _bankingtech_fk_backup_v5 LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I ADD CONSTRAINT %I %s',
            foreign_key.table_schema,
            foreign_key.table_name,
            foreign_key.constraint_name,
            foreign_key.constraint_definition
        );
    END LOOP;
END $$;

DROP TABLE _bankingtech_fk_backup_v5;
