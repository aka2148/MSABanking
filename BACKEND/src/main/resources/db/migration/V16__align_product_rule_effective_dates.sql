ALTER TABLE public.product_rule
    ALTER COLUMN pr_effective_from TYPE TIMESTAMP
        USING NULLIF(BTRIM(pr_effective_from), '')::TIMESTAMP,
    ALTER COLUMN pr_effective_to TYPE TIMESTAMP
        USING NULLIF(BTRIM(pr_effective_to), '')::TIMESTAMP;
