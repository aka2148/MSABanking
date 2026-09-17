CREATE TEMP TABLE _bankingtech_fk_backup AS
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
      c.relname IN ('users', 'user_credentials', 'customers', 'fd_accounts', 'fd_transactions', 'fd_product_ref')
      OR referenced_schema.nspname = 'public'
         AND referenced_table.relname IN (
             'users', 'customers', 'fd_accounts', 'fd_product_ref',
             'fd_user_ref', 'fd_rule_outcome_ref', 'fd_currency_ref', 'transaction_types'
         )
  );

DO $$
DECLARE
    foreign_key RECORD;
BEGIN
    FOR foreign_key IN SELECT * FROM _bankingtech_fk_backup LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I DROP CONSTRAINT %I',
            foreign_key.table_schema,
            foreign_key.table_name,
            foreign_key.constraint_name
        );
    END LOOP;
END $$;

ALTER TABLE public.users ALTER COLUMN usr_user_id TYPE BIGINT;
ALTER TABLE public.user_credentials
    ALTER COLUMN ucr_credential_id TYPE BIGINT,
    ALTER COLUMN ucr_user_id TYPE BIGINT;
ALTER TABLE public.customers
    ALTER COLUMN cus_customer_id TYPE BIGINT,
    ALTER COLUMN cus_user_id TYPE BIGINT,
    ALTER COLUMN cus_home_branch_id TYPE BIGINT,
    ALTER COLUMN cus_classification_type_id TYPE BIGINT;
ALTER TABLE public.fd_product_ref ALTER COLUMN fpr_product_id TYPE BIGINT;
ALTER TABLE public.fd_user_ref ALTER COLUMN fur_user_id TYPE BIGINT;
ALTER TABLE public.fd_rule_outcome_ref ALTER COLUMN fror_outcome_id TYPE BIGINT;
ALTER TABLE public.fd_currency_ref ALTER COLUMN fcr_currency_id TYPE BIGINT;
ALTER TABLE public.transaction_types ALTER COLUMN tt_txn_type_id TYPE BIGINT;
ALTER TABLE public.fd_accounts
    ALTER COLUMN fa_account_id TYPE BIGINT,
    ALTER COLUMN fa_primary_holder_id TYPE BIGINT,
    ALTER COLUMN fa_product_id TYPE BIGINT,
    ALTER COLUMN fa_applied_rate_outcome_id TYPE BIGINT,
    ALTER COLUMN fa_currency_id TYPE BIGINT;
ALTER TABLE public.fd_transactions
    ALTER COLUMN ft_transaction_id TYPE BIGINT,
    ALTER COLUMN ft_account_id TYPE BIGINT,
    ALTER COLUMN ft_txn_type_id TYPE BIGINT,
    ALTER COLUMN ft_currency_id TYPE BIGINT;

DO $$
DECLARE
    foreign_key RECORD;
BEGIN
    FOR foreign_key IN SELECT * FROM _bankingtech_fk_backup LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I ADD CONSTRAINT %I %s',
            foreign_key.table_schema,
            foreign_key.table_name,
            foreign_key.constraint_name,
            foreign_key.constraint_definition
        );
    END LOOP;
END $$;

DROP TABLE _bankingtech_fk_backup;
