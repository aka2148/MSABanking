-- Existing entity mappings contain these optional audit fields, but V1 omits them.
-- Nullable additions preserve existing records and do not change business rules.
ALTER TABLE public.gl_code_mapping ADD COLUMN IF NOT EXISTS gcm_operator_user_id INTEGER;
ALTER TABLE public.loan_repayment_schedule ADD COLUMN IF NOT EXISTS lrs_operator_user_id INTEGER;
ALTER TABLE public.sweep_config ADD COLUMN IF NOT EXISTS sc_operator_user_id INTEGER;
