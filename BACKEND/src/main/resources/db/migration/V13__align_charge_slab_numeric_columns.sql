ALTER TABLE public.charge_slab
    ALTER COLUMN cs_value_from TYPE NUMERIC(15,4)
        USING CASE
            WHEN NULLIF(BTRIM(cs_value_from), '') IS NULL THEN NULL
            WHEN BTRIM(cs_value_from) ~ '^[+-]?([0-9]+(\.[0-9]+)?|\.[0-9]+)$'
                THEN BTRIM(cs_value_from)::NUMERIC(15,4)
            ELSE NULL
        END,
    ALTER COLUMN cs_value_to TYPE NUMERIC(15,4)
        USING CASE
            WHEN NULLIF(BTRIM(cs_value_to), '') IS NULL THEN NULL
            WHEN BTRIM(cs_value_to) ~ '^[+-]?([0-9]+(\.[0-9]+)?|\.[0-9]+)$'
                THEN BTRIM(cs_value_to)::NUMERIC(15,4)
            ELSE NULL
        END,
    ALTER COLUMN cs_charge_amount TYPE NUMERIC(15,4)
        USING cs_charge_amount::NUMERIC(15,4);
