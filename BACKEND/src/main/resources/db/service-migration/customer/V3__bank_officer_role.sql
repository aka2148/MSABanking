INSERT INTO roles(rol_role_code,rol_role_name,rol_description)
SELECT 'ROLE_BANK_OFFICER','Bank Officer','Staff access to customer and deposit operations'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE rol_role_code='ROLE_BANK_OFFICER');
