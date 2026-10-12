ALTER TABLE public.rule_outcome
    ALTER COLUMN ro_outcome_value TYPE NUMERIC(22, 8)
        USING ro_outcome_value::NUMERIC(22, 8);

ALTER TABLE public.charge_slab
    ALTER COLUMN cs_charge_amount TYPE NUMERIC(22, 8)
        USING cs_charge_amount::NUMERIC(22, 8);
