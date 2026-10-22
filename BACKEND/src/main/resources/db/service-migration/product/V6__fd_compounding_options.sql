-- The lifecycle supports these capitalization periods. Defaults stay unchanged.
UPDATE attribute_definitions SET ad_choice_values='MONTHLY,QUARTERLY,YEARLY'
WHERE ad_behavior_key='FD_COMPOUNDING' AND ad_attribute_key='COMPOUNDING_BASIS';
-- Periodic payout is not yet supported by the existing settlement contract.
UPDATE attribute_definitions SET ad_choice_values='AT_MATURITY'
WHERE ad_behavior_key='FD_PAYOUT' AND ad_attribute_key='PAYOUT_FREQUENCY';
