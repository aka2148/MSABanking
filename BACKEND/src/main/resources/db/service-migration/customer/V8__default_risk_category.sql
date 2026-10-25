UPDATE customers SET cus_risk_category='LOW' WHERE cus_risk_category IS NULL OR TRIM(cus_risk_category)='';
ALTER TABLE customers MODIFY cus_risk_category VARCHAR(30) NULL DEFAULT 'LOW';
