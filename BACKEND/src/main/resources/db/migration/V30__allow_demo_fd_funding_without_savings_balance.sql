-- FD demonstrations may fund the booked principal even when a savings account
-- has no seeded opening balance. The funding journal still records the debit.
ALTER TABLE public.savings_accounts
    DROP CONSTRAINT IF EXISTS savings_accounts_sa_balance_check;
