ALTER TABLE public.product_currency_config
    ADD COLUMN IF NOT EXISTS pcc_minimum_amount NUMERIC(22, 8),
    ADD COLUMN IF NOT EXISTS pcc_maximum_amount NUMERIC(22, 8);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_product_currency_config_amount_limits'
    ) THEN
        ALTER TABLE public.product_currency_config
            ADD CONSTRAINT ck_product_currency_config_amount_limits
            CHECK (
                (pcc_minimum_amount IS NULL AND pcc_maximum_amount IS NULL)
                OR (
                    pcc_minimum_amount >= 0
                    AND pcc_maximum_amount > 0
                    AND pcc_minimum_amount <= pcc_maximum_amount
                )
            );
    END IF;
END
$$;
