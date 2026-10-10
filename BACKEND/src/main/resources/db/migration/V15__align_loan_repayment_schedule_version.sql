ALTER TABLE public.loan_repayment_schedule
    ALTER COLUMN lrs_version TYPE VARCHAR(20)
        USING lrs_version::TEXT;
