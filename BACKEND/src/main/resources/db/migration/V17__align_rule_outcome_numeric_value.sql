ALTER TABLE public.rule_outcome
    ALTER COLUMN ro_outcome_value TYPE NUMERIC(15,4)
        USING CASE
            WHEN NULLIF(BTRIM(ro_outcome_value), '') IS NULL THEN NULL
            WHEN BTRIM(ro_outcome_value) ~ '^[+-]?([0-9]+(\.[0-9]+)?|\.[0-9]+)$'
                THEN BTRIM(ro_outcome_value)::NUMERIC(15,4)
            ELSE NULL
        END;
