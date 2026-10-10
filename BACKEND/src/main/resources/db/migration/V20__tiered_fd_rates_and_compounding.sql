DO $$
BEGIN
    IF EXISTS (
        SELECT upper(fpr_product_code) FROM public.fd_product_ref
        GROUP BY upper(fpr_product_code) HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V20 invariant violation: duplicate fd_product_ref product codes';
    END IF;
    IF EXISTS (
        SELECT upper(fcr_iso_code) FROM public.fd_currency_ref
        GROUP BY upper(fcr_iso_code) HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V20 invariant violation: duplicate fd_currency_ref ISO codes';
    END IF;
    IF EXISTS (
        SELECT upper(cur_iso_code) FROM public.currency
        GROUP BY upper(cur_iso_code) HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V20 invariant violation: duplicate normalized currency ISO codes ignoring case';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_fd_product_ref_product_code
    ON public.fd_product_ref (upper(fpr_product_code));

CREATE UNIQUE INDEX IF NOT EXISTS uq_fd_currency_ref_iso_code
    ON public.fd_currency_ref (upper(fcr_iso_code));

ALTER TABLE public.fd_rule_outcome_ref
    ADD COLUMN IF NOT EXISTS fror_source_outcome_id INT
        REFERENCES public.rule_outcome(ro_outcome_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_fd_rule_outcome_ref_source
    ON public.fd_rule_outcome_ref (fror_source_outcome_id)
    WHERE fror_source_outcome_id IS NOT NULL;

INSERT INTO public.rule_type (rut_rule_type_code, rut_description)
VALUES ('FD_TENURE_RATE', 'FD rate outcome selected by currency and tenure band'),
       ('FD_AMOUNT_RANGE', 'FD principal eligibility range')
ON CONFLICT (rut_rule_type_code) DO NOTHING;

INSERT INTO public.product_category (pc_category_code, pc_category_name)
VALUES ('FIXED_DEPOSIT', 'Fixed Deposits')
ON CONFLICT (pc_category_code) DO NOTHING;

INSERT INTO public.currency
    (cur_iso_code, cur_currency_name, cur_currency_symbol, cur_decimal_places, cur_is_active)
SELECT 'USD', 'US Dollar', '$', 2, TRUE
WHERE NOT EXISTS (SELECT 1 FROM public.currency WHERE upper(cur_iso_code) = 'USD');

INSERT INTO public.currency
    (cur_iso_code, cur_currency_name, cur_currency_symbol, cur_decimal_places, cur_is_active)
SELECT 'INR', 'Indian Rupee', '₹', 2, TRUE
WHERE NOT EXISTS (SELECT 1 FROM public.currency WHERE upper(cur_iso_code) = 'INR');

INSERT INTO public.fd_currency_ref (fcr_iso_code, fcr_currency_name, fcr_decimal_places)
SELECT cur_iso_code, cur_currency_name, cur_decimal_places
FROM public.currency
WHERE upper(cur_iso_code) IN ('USD', 'INR')
ON CONFLICT DO NOTHING;

ALTER TABLE public.fd_interest_accrual
    ALTER COLUMN fia_accrued_amount TYPE NUMERIC(22, 8)
        USING fia_accrued_amount::NUMERIC(22, 8),
    ALTER COLUMN fia_cumulative_accrued TYPE NUMERIC(22, 8)
        USING fia_cumulative_accrued::NUMERIC(22, 8);

ALTER TABLE public.fd_interest_accrual
    ADD COLUMN IF NOT EXISTS fia_compounding_balance NUMERIC(22, 8),
    ADD COLUMN IF NOT EXISTS fia_capitalized_amount NUMERIC(22, 8) NOT NULL DEFAULT 0;

ALTER TABLE public.fd_interest_accrual
    ADD CONSTRAINT ck_fd_interest_accrual_capitalized_nonnegative
        CHECK (fia_capitalized_amount >= 0);
