UPDATE public.attribute_definitions
SET ad_default_value = ad_choice_values
WHERE ad_behavior_key IN ('LOAN_REPAYMENT_METHOD', 'CASA_INTEREST_METHOD', 'FD_COMPOUNDING', 'FD_PAYOUT')
  AND ad_default_value IS NULL;

UPDATE public.attribute_definitions
SET ad_is_required = FALSE
WHERE ad_behavior_key IN ('FOREX_FUNDING_CURRENCY', 'FOREX_DESTINATION_CURRENCIES');
