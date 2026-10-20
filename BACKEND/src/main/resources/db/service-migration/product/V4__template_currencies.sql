-- INR is required by the existing Forex funding-currency attribute and the
-- sample Indian banking product templates. USD is the sample destination.
-- Preserve any currency already set up.
INSERT INTO currency(cur_iso_code,cur_currency_name,cur_currency_symbol,cur_decimal_places,cur_is_active,cur_created_by)
SELECT 'INR','Indian Rupee','₹',2,1,'FLYWAY'
WHERE NOT EXISTS (SELECT 1 FROM currency WHERE UPPER(cur_iso_code)='INR');

INSERT INTO currency(cur_iso_code,cur_currency_name,cur_currency_symbol,cur_decimal_places,cur_is_active,cur_created_by)
SELECT 'USD','US Dollar','$',2,1,'FLYWAY'
WHERE NOT EXISTS (SELECT 1 FROM currency WHERE UPPER(cur_iso_code)='USD');
