INSERT INTO currency(cur_iso_code,cur_iso_numeric_code,cur_currency_name,cur_currency_symbol,cur_decimal_places,cur_is_active,cur_created_by)
SELECT 'JPY','392','Japanese Yen','¥',0,1,'FLYWAY'
WHERE NOT EXISTS (SELECT 1 FROM currency WHERE UPPER(cur_iso_code)='JPY');
