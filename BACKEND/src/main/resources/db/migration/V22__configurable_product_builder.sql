ALTER TABLE public.attribute_definitions
    ADD COLUMN IF NOT EXISTS ad_choice_values VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS ad_unit VARCHAR(30),
    ADD COLUMN IF NOT EXISTS ad_default_value VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ad_min_value NUMERIC(22, 8),
    ADD COLUMN IF NOT EXISTS ad_max_value NUMERIC(22, 8),
    ADD COLUMN IF NOT EXISTS ad_display_order INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ad_behavior_key VARCHAR(50);

ALTER TABLE public.charge_slab
    ADD COLUMN IF NOT EXISTS cs_charge_unit VARCHAR(30) NOT NULL DEFAULT 'FIXED_AMOUNT',
    ADD COLUMN IF NOT EXISTS cs_minimum_charge NUMERIC(22, 8),
    ADD COLUMN IF NOT EXISTS cs_maximum_charge NUMERIC(22, 8);

CREATE UNIQUE INDEX IF NOT EXISTS uq_attribute_definition_category_key
    ON public.attribute_definitions (ad_category_id, ad_attribute_key);

CREATE UNIQUE INDEX IF NOT EXISTS uq_product_currency_config_product_currency
    ON public.product_currency_config (pcc_product_id, pcc_currency_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_product_currency_config_one_base
    ON public.product_currency_config (pcc_product_id)
    WHERE pcc_is_base_currency;

CREATE TABLE IF NOT EXISTS public.product_configuration_revision (
    pcr_revision_id BIGSERIAL PRIMARY KEY,
    pcr_product_id INT NOT NULL REFERENCES public.product(pro_product_id),
    pcr_revision_no INT NOT NULL,
    pcr_status VARCHAR(20) NOT NULL,
    pcr_snapshot_json JSONB NOT NULL,
    pcr_created_by VARCHAR(100) NOT NULL,
    pcr_created_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_configuration_revision UNIQUE (pcr_product_id, pcr_revision_no),
    CONSTRAINT ck_product_configuration_revision_status CHECK (pcr_status IN ('DRAFT', 'ACTIVE'))
);

INSERT INTO public.currency
    (cur_iso_code, cur_currency_name, cur_currency_symbol, cur_decimal_places, cur_is_active)
SELECT 'KWD', 'Kuwaiti Dinar', 'KD', 3, TRUE
WHERE NOT EXISTS (SELECT 1 FROM public.currency WHERE upper(cur_iso_code) = 'KWD');

INSERT INTO public.product_category (pc_category_code, pc_category_name)
VALUES ('LOAN', 'Loans'),
       ('CASA', 'Current and Savings Accounts'),
       ('FIXED_DEPOSIT', 'Fixed Deposits'),
       ('FOREX', 'Foreign Exchange')
ON CONFLICT (pc_category_code) DO NOTHING;

INSERT INTO public.product_category (pc_category_code, pc_category_name, pc_parent_category_id)
SELECT 'SAVINGS', 'Savings Account', pc_category_id
FROM public.product_category WHERE pc_category_code = 'CASA'
ON CONFLICT (pc_category_code) DO NOTHING;

INSERT INTO public.product_category (pc_category_code, pc_category_name, pc_parent_category_id)
SELECT 'CURRENT', 'Current Account', pc_category_id
FROM public.product_category WHERE pc_category_code = 'CASA'
ON CONFLICT (pc_category_code) DO NOTHING;

INSERT INTO public.rule_type (rut_rule_type_code, rut_description)
VALUES ('ELIGIBILITY', 'Typed customer or application eligibility rule'),
       ('PRICING_BAND', 'Typed product pricing band')
ON CONFLICT (rut_rule_type_code) DO NOTHING;

INSERT INTO public.attribute_definitions
    (ad_category_id, ad_attribute_key, ad_data_type, ad_display_name, ad_is_required,
     ad_choice_values, ad_unit, ad_min_value, ad_max_value, ad_display_order, ad_behavior_key)
SELECT pc_category_id, values.attribute_key, values.data_type, values.display_name, values.required,
       values.choice_values, values.unit, values.min_value, values.max_value::NUMERIC(22, 8), values.display_order,
       values.behavior_key
FROM public.product_category
JOIN (VALUES
    ('LOAN', 'MIN_AMOUNT', 'NUMBER', 'Minimum loan amount', TRUE, NULL, 'MONEY', 0.01, NULL, 10, 'LOAN_AMOUNT_LIMIT'),
    ('LOAN', 'MAX_AMOUNT', 'NUMBER', 'Maximum loan amount', TRUE, NULL, 'MONEY', 0.01, NULL, 20, 'LOAN_AMOUNT_LIMIT'),
    ('LOAN', 'MIN_TENURE_MONTHS', 'NUMBER', 'Minimum tenure', TRUE, NULL, 'MONTHS', 1, NULL, 30, 'LOAN_TENURE_LIMIT'),
    ('LOAN', 'MAX_TENURE_MONTHS', 'NUMBER', 'Maximum tenure', TRUE, NULL, 'MONTHS', 1, NULL, 40, 'LOAN_TENURE_LIMIT'),
    ('LOAN', 'REPAYMENT_METHOD', 'STRING', 'Repayment method', TRUE, 'REDUCING_BALANCE', NULL, NULL, NULL, 50, 'LOAN_REPAYMENT_METHOD'),
    ('CASA', 'MINIMUM_BALANCE', 'NUMBER', 'Minimum balance', TRUE, NULL, 'MONEY', 0, NULL, 10, 'CASA_BALANCE_REQUIREMENT'),
    ('CASA', 'INTEREST_CALCULATION', 'STRING', 'Interest calculation', TRUE, 'DAILY_BALANCE', NULL, NULL, NULL, 20, 'CASA_INTEREST_METHOD'),
    ('FIXED_DEPOSIT', 'COMPOUNDING_BASIS', 'STRING', 'Compounding basis', TRUE, 'QUARTERLY', NULL, NULL, NULL, 10, 'FD_COMPOUNDING'),
    ('FIXED_DEPOSIT', 'PAYOUT_FREQUENCY', 'STRING', 'Payout frequency', TRUE, 'AT_MATURITY', NULL, NULL, NULL, 20, 'FD_PAYOUT'),
    ('FOREX', 'FUNDING_CURRENCY', 'STRING', 'Funding currency', TRUE, 'INR', NULL, NULL, NULL, 10, 'FOREX_FUNDING_CURRENCY'),
    ('FOREX', 'DESTINATION_CURRENCIES', 'STRING', 'Destination currencies', TRUE, 'USD,KWD', NULL, NULL, NULL, 20, 'FOREX_DESTINATION_CURRENCIES')
) AS values(category_code, attribute_key, data_type, display_name, required, choice_values,
            unit, min_value, max_value, display_order, behavior_key)
    ON product_category.pc_category_code = values.category_code
ON CONFLICT (ad_category_id, ad_attribute_key) DO NOTHING;
