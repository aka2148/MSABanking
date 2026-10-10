ALTER TABLE public.fd_accounts
    ALTER COLUMN fa_row_version TYPE BIGINT
        USING CASE
            WHEN NULLIF(BTRIM(fa_row_version), '') IS NULL THEN 0
            WHEN BTRIM(fa_row_version) ~ '^[+-]?[0-9]+$' THEN BTRIM(fa_row_version)::BIGINT
            ELSE 0
        END;
