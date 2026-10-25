-- Restores the legacy host/local/CRUD audit metadata for this MySQL service.
-- MySQL initializes and refreshes both UTC timestamps; CRUD defaults to C on insert.
-- Column defaults and ON UPDATE clauses cover all application and direct SQL write paths.

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' AND column_name='usr_host_ts'), 'SELECT 1', 'ALTER TABLE `users` ADD COLUMN `usr_host_ts` DATETIME(6) NULL, ADD COLUMN `usr_local_ts` DATETIME(6) NULL, ADD COLUMN `usr_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `users` SET `usr_host_ts`=COALESCE(`usr_updated_date`,`usr_created_date`,UTC_TIMESTAMP(6)), `usr_local_ts`=COALESCE(`usr_updated_date`,`usr_created_date`,UTC_TIMESTAMP(6)), `usr_crud_value`=CASE WHEN `usr_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `users` MODIFY COLUMN `usr_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `usr_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `usr_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='user_credentials' AND column_name='ucr_host_ts'), 'SELECT 1', 'ALTER TABLE `user_credentials` ADD COLUMN `ucr_host_ts` DATETIME(6) NULL, ADD COLUMN `ucr_local_ts` DATETIME(6) NULL, ADD COLUMN `ucr_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `user_credentials` SET `ucr_host_ts`=COALESCE(`ucr_updated_date`,`ucr_created_date`,UTC_TIMESTAMP(6)), `ucr_local_ts`=COALESCE(`ucr_updated_date`,`ucr_created_date`,UTC_TIMESTAMP(6)), `ucr_crud_value`=CASE WHEN `ucr_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `user_credentials` MODIFY COLUMN `ucr_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `ucr_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `ucr_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='user_password_history' AND column_name='uph_host_ts'), 'SELECT 1', 'ALTER TABLE `user_password_history` ADD COLUMN `uph_host_ts` DATETIME(6) NULL, ADD COLUMN `uph_local_ts` DATETIME(6) NULL, ADD COLUMN `uph_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `user_password_history` SET `uph_host_ts`=COALESCE(`uph_updated_date`,`uph_created_date`,UTC_TIMESTAMP(6)), `uph_local_ts`=COALESCE(`uph_updated_date`,`uph_created_date`,UTC_TIMESTAMP(6)), `uph_crud_value`=CASE WHEN `uph_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `user_password_history` MODIFY COLUMN `uph_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `uph_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `uph_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='user_roles' AND column_name='ur_host_ts'), 'SELECT 1', 'ALTER TABLE `user_roles` ADD COLUMN `ur_host_ts` DATETIME(6) NULL, ADD COLUMN `ur_local_ts` DATETIME(6) NULL, ADD COLUMN `ur_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `user_roles` SET `ur_host_ts`=COALESCE(`ur_updated_date`,`ur_created_date`,UTC_TIMESTAMP(6)), `ur_local_ts`=COALESCE(`ur_updated_date`,`ur_created_date`,UTC_TIMESTAMP(6)), `ur_crud_value`=CASE WHEN `ur_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `user_roles` MODIFY COLUMN `ur_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `ur_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `ur_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='roles' AND column_name='rol_host_ts'), 'SELECT 1', 'ALTER TABLE `roles` ADD COLUMN `rol_host_ts` DATETIME(6) NULL, ADD COLUMN `rol_local_ts` DATETIME(6) NULL, ADD COLUMN `rol_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `roles` SET `rol_host_ts`=COALESCE(`rol_updated_date`,`rol_created_date`,UTC_TIMESTAMP(6)), `rol_local_ts`=COALESCE(`rol_updated_date`,`rol_created_date`,UTC_TIMESTAMP(6)), `rol_crud_value`=CASE WHEN `rol_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `roles` MODIFY COLUMN `rol_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `rol_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `rol_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='customers' AND column_name='cus_host_ts'), 'SELECT 1', 'ALTER TABLE `customers` ADD COLUMN `cus_host_ts` DATETIME(6) NULL, ADD COLUMN `cus_local_ts` DATETIME(6) NULL, ADD COLUMN `cus_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `customers` SET `cus_host_ts`=COALESCE(`cus_updated_date`,`cus_created_date`,UTC_TIMESTAMP(6)), `cus_local_ts`=COALESCE(`cus_updated_date`,`cus_created_date`,UTC_TIMESTAMP(6)), `cus_crud_value`=CASE WHEN `cus_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `customers` MODIFY COLUMN `cus_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cus_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cus_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='customer_details' AND column_name='cud_host_ts'), 'SELECT 1', 'ALTER TABLE `customer_details` ADD COLUMN `cud_host_ts` DATETIME(6) NULL, ADD COLUMN `cud_local_ts` DATETIME(6) NULL, ADD COLUMN `cud_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `customer_details` SET `cud_host_ts`=COALESCE(`cud_updated_date`,`cud_created_date`,UTC_TIMESTAMP(6)), `cud_local_ts`=COALESCE(`cud_updated_date`,`cud_created_date`,UTC_TIMESTAMP(6)), `cud_crud_value`=CASE WHEN `cud_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `customer_details` MODIFY COLUMN `cud_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cud_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cud_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='customer_addresses' AND column_name='cad_host_ts'), 'SELECT 1', 'ALTER TABLE `customer_addresses` ADD COLUMN `cad_host_ts` DATETIME(6) NULL, ADD COLUMN `cad_local_ts` DATETIME(6) NULL, ADD COLUMN `cad_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `customer_addresses` SET `cad_host_ts`=COALESCE(`cad_updated_date`,`cad_created_date`,UTC_TIMESTAMP(6)), `cad_local_ts`=COALESCE(`cad_updated_date`,`cad_created_date`,UTC_TIMESTAMP(6)), `cad_crud_value`=CASE WHEN `cad_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `customer_addresses` MODIFY COLUMN `cad_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cad_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cad_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='customer_emails' AND column_name='cem_host_ts'), 'SELECT 1', 'ALTER TABLE `customer_emails` ADD COLUMN `cem_host_ts` DATETIME(6) NULL, ADD COLUMN `cem_local_ts` DATETIME(6) NULL, ADD COLUMN `cem_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `customer_emails` SET `cem_host_ts`=COALESCE(`cem_updated_date`,`cem_created_date`,UTC_TIMESTAMP(6)), `cem_local_ts`=COALESCE(`cem_updated_date`,`cem_created_date`,UTC_TIMESTAMP(6)), `cem_crud_value`=CASE WHEN `cem_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `customer_emails` MODIFY COLUMN `cem_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cem_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cem_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='customer_phone_numbers' AND column_name='cph_host_ts'), 'SELECT 1', 'ALTER TABLE `customer_phone_numbers` ADD COLUMN `cph_host_ts` DATETIME(6) NULL, ADD COLUMN `cph_local_ts` DATETIME(6) NULL, ADD COLUMN `cph_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `customer_phone_numbers` SET `cph_host_ts`=COALESCE(`cph_updated_date`,`cph_created_date`,UTC_TIMESTAMP(6)), `cph_local_ts`=COALESCE(`cph_updated_date`,`cph_created_date`,UTC_TIMESTAMP(6)), `cph_crud_value`=CASE WHEN `cph_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `customer_phone_numbers` MODIFY COLUMN `cph_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cph_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cph_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='customer_identity_proofs' AND column_name='cip_host_ts'), 'SELECT 1', 'ALTER TABLE `customer_identity_proofs` ADD COLUMN `cip_host_ts` DATETIME(6) NULL, ADD COLUMN `cip_local_ts` DATETIME(6) NULL, ADD COLUMN `cip_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `customer_identity_proofs` SET `cip_host_ts`=COALESCE(`cip_updated_date`,`cip_created_date`,UTC_TIMESTAMP(6)), `cip_local_ts`=COALESCE(`cip_updated_date`,`cip_created_date`,UTC_TIMESTAMP(6)), `cip_crud_value`=CASE WHEN `cip_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `customer_identity_proofs` MODIFY COLUMN `cip_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cip_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cip_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='customer_classification_types' AND column_name='cct_host_ts'), 'SELECT 1', 'ALTER TABLE `customer_classification_types` ADD COLUMN `cct_host_ts` DATETIME(6) NULL, ADD COLUMN `cct_local_ts` DATETIME(6) NULL, ADD COLUMN `cct_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `customer_classification_types` SET `cct_host_ts`=COALESCE(`cct_updated_date`,`cct_created_date`,UTC_TIMESTAMP(6)), `cct_local_ts`=COALESCE(`cct_updated_date`,`cct_created_date`,UTC_TIMESTAMP(6)), `cct_crud_value`=CASE WHEN `cct_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `customer_classification_types` MODIFY COLUMN `cct_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cct_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cct_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='branches' AND column_name='bra_host_ts'), 'SELECT 1', 'ALTER TABLE `branches` ADD COLUMN `bra_host_ts` DATETIME(6) NULL, ADD COLUMN `bra_local_ts` DATETIME(6) NULL, ADD COLUMN `bra_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `branches` SET `bra_host_ts`=COALESCE(`bra_updated_date`,`bra_created_date`,UTC_TIMESTAMP(6)), `bra_local_ts`=COALESCE(`bra_updated_date`,`bra_created_date`,UTC_TIMESTAMP(6)), `bra_crud_value`=CASE WHEN `bra_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `branches` MODIFY COLUMN `bra_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `bra_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `bra_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='employee_profile' AND column_name='emp_host_ts'), 'SELECT 1', 'ALTER TABLE `employee_profile` ADD COLUMN `emp_host_ts` DATETIME(6) NULL, ADD COLUMN `emp_local_ts` DATETIME(6) NULL, ADD COLUMN `emp_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `employee_profile` SET `emp_host_ts`=COALESCE(`emp_updated_date`,`emp_created_date`,UTC_TIMESTAMP(6)), `emp_local_ts`=COALESCE(`emp_updated_date`,`emp_created_date`,UTC_TIMESTAMP(6)), `emp_crud_value`=CASE WHEN `emp_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `employee_profile` MODIFY COLUMN `emp_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `emp_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `emp_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';
