-- Paired immutable postings for account-to-account savings transfers.
CREATE TABLE public.savings_transactions (
    st_id BIGSERIAL PRIMARY KEY,
    st_reference_id VARCHAR(64) NOT NULL,
    st_account_id BIGINT NOT NULL REFERENCES public.savings_accounts(sa_account_id),
    st_entry_type VARCHAR(10) NOT NULL CHECK (st_entry_type IN ('DEBIT', 'CREDIT')),
    st_amount NUMERIC(18,4) NOT NULL CHECK (st_amount > 0),
    st_currency_id BIGINT NOT NULL REFERENCES public.fd_currency_ref(fcr_currency_id),
    st_balance_after NUMERIC(18,4) NOT NULL CHECK (st_balance_after >= 0),
    st_remarks VARCHAR(500),
    st_posted_at TIMESTAMPTZ NOT NULL,
    st_uuid VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    CONSTRAINT uq_savings_transaction_reference_side UNIQUE (st_reference_id, st_entry_type),
    CONSTRAINT uq_savings_transaction_uuid UNIQUE (st_uuid)
);
CREATE INDEX idx_savings_transactions_account_posted
    ON public.savings_transactions (st_account_id, st_posted_at, st_id);
CREATE INDEX idx_savings_transactions_reference
    ON public.savings_transactions (st_reference_id);

-- The repository ships no named FD product seeds. Normalize any already-configured
-- tiered offers to the demonstration policy based on their longest offered tenor.
WITH fd_tenors AS (
    SELECT p.pro_product_id,
           c.cur_currency_id,
           upper(c.cur_iso_code) AS currency_code,
           MAX(tenure.rc_value_to::integer) AS maximum_tenure_months
      FROM public.product p
      JOIN public.product_category pc ON pc.pc_category_id = p.pro_category_id
      JOIN public.fd_product_ref fpr ON upper(fpr.fpr_product_code) = upper(p.pro_product_code)
      JOIN public.product_currency_config pcc ON pcc.pcc_product_id = p.pro_product_id
      JOIN public.currency c ON c.cur_currency_id = pcc.pcc_currency_id
      JOIN public.product_rule pr ON pr.pr_product_id = p.pro_product_id
           AND upper(pr.pr_rule_name) = 'TENURE_RATE' AND pr.pr_is_active
      JOIN public.rule_condition tenure ON tenure.rc_rule_id = pr.pr_rule_id
           AND upper(tenure.rc_condition_attribute) = 'TENURE_MONTHS'
     WHERE upper(pc.pc_category_code) = 'FIXED_DEPOSIT'
     GROUP BY p.pro_product_id, c.cur_currency_id, upper(c.cur_iso_code)
), fd_limits AS (
    SELECT pro_product_id, cur_currency_id,
           CASE currency_code
             WHEN 'INR' THEN CASE WHEN maximum_tenure_months <= 12 THEN 5000
                                  WHEN maximum_tenure_months <= 36 THEN 10000 ELSE 25000 END
             WHEN 'USD' THEN CASE WHEN maximum_tenure_months <= 12 THEN 100
                                  WHEN maximum_tenure_months <= 36 THEN 250 ELSE 500 END
             WHEN 'KWD' THEN CASE WHEN maximum_tenure_months <= 12 THEN 50
                                  WHEN maximum_tenure_months <= 36 THEN 100 ELSE 250 END
           END::NUMERIC(22,8) AS minimum_amount,
           CASE currency_code
             WHEN 'INR' THEN CASE WHEN maximum_tenure_months <= 12 THEN 10000000
                                  WHEN maximum_tenure_months <= 36 THEN 15000000 ELSE 20000000 END
             WHEN 'USD' THEN CASE WHEN maximum_tenure_months <= 12 THEN 1000000
                                  WHEN maximum_tenure_months <= 36 THEN 1500000 ELSE 2000000 END
             WHEN 'KWD' THEN CASE WHEN maximum_tenure_months <= 12 THEN 250000
                                  WHEN maximum_tenure_months <= 36 THEN 375000 ELSE 500000 END
           END::NUMERIC(22,8) AS maximum_amount
      FROM fd_tenors
)
UPDATE public.product_currency_config pcc
   SET pcc_minimum_amount = limits.minimum_amount,
       pcc_maximum_amount = limits.maximum_amount
  FROM fd_limits limits
 WHERE pcc.pcc_product_id = limits.pro_product_id
   AND pcc.pcc_currency_id = limits.cur_currency_id
   AND limits.minimum_amount IS NOT NULL;

-- Keep the rule conditions used by FD booking in sync with the public schedule bounds.
UPDATE public.rule_condition amount
   SET rc_value_from = pcc.pcc_minimum_amount::TEXT,
       rc_value_to = pcc.pcc_maximum_amount::TEXT
  FROM public.product_rule pr
  JOIN public.product p ON p.pro_product_id = pr.pr_product_id
  JOIN public.product_category pc ON pc.pc_category_id = p.pro_category_id
  JOIN public.fd_product_ref fpr ON upper(fpr.fpr_product_code) = upper(p.pro_product_code)
  JOIN public.product_currency_config pcc ON pcc.pcc_product_id = p.pro_product_id
  JOIN public.currency c ON c.cur_currency_id = pcc.pcc_currency_id
  JOIN public.rule_outcome ro ON ro.ro_rule_id = pr.pr_rule_id
       AND ro.ro_currency_id = c.cur_currency_id
       AND upper(ro.ro_outcome_type) = 'INTEREST_RATE'
 WHERE amount.rc_rule_id = pr.pr_rule_id
   AND upper(pr.pr_rule_name) = 'TENURE_RATE'
   AND pr.pr_is_active
   AND upper(pc.pc_category_code) = 'FIXED_DEPOSIT'
   AND upper(amount.rc_condition_attribute) = 'AMOUNT_RANGE'
   AND pcc.pcc_minimum_amount IS NOT NULL
   AND pcc.pcc_maximum_amount IS NOT NULL;
