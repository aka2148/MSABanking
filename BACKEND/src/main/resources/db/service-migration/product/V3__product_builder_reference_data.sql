-- Reference vocabulary used by the staff product-configuration builder.
INSERT INTO currency(cur_iso_code,cur_currency_name,cur_currency_symbol,cur_decimal_places,cur_is_active,cur_created_by)
SELECT 'KWD','Kuwaiti Dinar','KD',3,1,'FLYWAY'
WHERE NOT EXISTS (SELECT 1 FROM currency WHERE UPPER(cur_iso_code)='KWD');

INSERT INTO product_category(pc_category_code,pc_category_name,pc_created_by)
SELECT v.code,v.name,'FLYWAY' FROM (
    SELECT 'LOAN' code,'Loans' name UNION ALL
    SELECT 'CASA','Current and Savings Accounts' UNION ALL
    SELECT 'FIXED_DEPOSIT','Fixed Deposits' UNION ALL
    SELECT 'FOREX','Foreign Exchange'
) v WHERE NOT EXISTS (SELECT 1 FROM product_category c WHERE UPPER(c.pc_category_code)=v.code);

INSERT INTO product_category(pc_category_code,pc_category_name,pc_parent_category_id,pc_created_by)
SELECT v.code,v.name,p.pc_category_id,'FLYWAY' FROM (
    SELECT 'SAVINGS' code,'Savings Account' name,'CASA' parent_code UNION ALL
    SELECT 'CURRENT','Current Account','CASA'
) v JOIN product_category p ON UPPER(p.pc_category_code)=v.parent_code
WHERE NOT EXISTS (SELECT 1 FROM product_category c WHERE UPPER(c.pc_category_code)=v.code);

INSERT INTO rule_type(rut_rule_type_code,rut_description,rut_created_by)
SELECT v.code,v.description,'FLYWAY' FROM (
    SELECT 'ELIGIBILITY' code,'Typed customer or application eligibility rule' description UNION ALL
    SELECT 'PRICING_BAND','Typed product pricing band'
) v WHERE NOT EXISTS (SELECT 1 FROM rule_type r WHERE UPPER(r.rut_rule_type_code)=v.code);

INSERT INTO role_type(rot_role_code,rot_description,rot_created_by)
SELECT v.code,v.description,'FLYWAY' FROM (
    SELECT 'PRIMARY_HOLDER' code,'Primary account or product holder' description UNION ALL
    SELECT 'JOINT_HOLDER','Additional joint holder' UNION ALL
    SELECT 'NOMINEE','Beneficiary nominee'
) v WHERE NOT EXISTS (SELECT 1 FROM role_type r WHERE UPPER(r.rot_role_code)=v.code);

INSERT IGNORE INTO attribute_definitions(ad_category_id,ad_attribute_key,ad_data_type,ad_display_name,ad_is_required,ad_choice_values,ad_unit,ad_min_value,ad_max_value,ad_display_order,ad_behavior_key,ad_created_by)
SELECT c.pc_category_id,v.attribute_key,v.data_type,v.display_name,v.required,v.choice_values,v.unit,v.min_value,v.max_value,v.display_order,v.behavior_key,'FLYWAY'
FROM product_category c JOIN (
    SELECT 'LOAN' category_code,'MIN_AMOUNT' attribute_key,'NUMBER' data_type,'Minimum loan amount' display_name,1 required,NULL choice_values,'MONEY' unit,0.01 min_value,NULL max_value,10 display_order,'LOAN_AMOUNT_LIMIT' behavior_key UNION ALL
    SELECT 'LOAN','MAX_AMOUNT','NUMBER','Maximum loan amount',1,NULL,'MONEY',0.01,NULL,20,'LOAN_AMOUNT_LIMIT' UNION ALL
    SELECT 'LOAN','MIN_TENURE_MONTHS','NUMBER','Minimum tenure',1,NULL,'MONTHS',1,NULL,30,'LOAN_TENURE_LIMIT' UNION ALL
    SELECT 'LOAN','MAX_TENURE_MONTHS','NUMBER','Maximum tenure',1,NULL,'MONTHS',1,NULL,40,'LOAN_TENURE_LIMIT' UNION ALL
    SELECT 'LOAN','REPAYMENT_METHOD','STRING','Repayment method',1,'REDUCING_BALANCE',NULL,NULL,NULL,50,'LOAN_REPAYMENT_METHOD' UNION ALL
    SELECT 'CASA','MINIMUM_BALANCE','NUMBER','Minimum balance',1,NULL,'MONEY',0,NULL,10,'CASA_BALANCE_REQUIREMENT' UNION ALL
    SELECT 'CASA','INTEREST_CALCULATION','STRING','Interest calculation',1,'DAILY_BALANCE',NULL,NULL,NULL,20,'CASA_INTEREST_METHOD' UNION ALL
    SELECT 'FIXED_DEPOSIT','COMPOUNDING_BASIS','STRING','Compounding basis',1,'QUARTERLY',NULL,NULL,NULL,10,'FD_COMPOUNDING' UNION ALL
    SELECT 'FIXED_DEPOSIT','PAYOUT_FREQUENCY','STRING','Payout frequency',1,'AT_MATURITY',NULL,NULL,NULL,20,'FD_PAYOUT' UNION ALL
    SELECT 'FOREX','FUNDING_CURRENCY','STRING','Funding currency',1,'INR',NULL,NULL,NULL,10,'FOREX_FUNDING_CURRENCY' UNION ALL
    SELECT 'FOREX','DESTINATION_CURRENCIES','STRING','Destination currencies',1,'USD,KWD',NULL,NULL,NULL,20,'FOREX_DESTINATION_CURRENCIES'
) v ON UPPER(c.pc_category_code)=v.category_code;
