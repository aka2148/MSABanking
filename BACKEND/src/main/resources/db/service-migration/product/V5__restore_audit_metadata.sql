-- Restores the legacy host/local/CRUD audit metadata for this MySQL service.
-- MySQL initializes and refreshes both UTC timestamps; CRUD defaults to C on insert.
-- Column defaults and ON UPDATE clauses cover all application and direct SQL write paths.

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product' AND column_name='pro_host_ts'), 'SELECT 1', 'ALTER TABLE `product` ADD COLUMN `pro_host_ts` DATETIME(6) NULL, ADD COLUMN `pro_local_ts` DATETIME(6) NULL, ADD COLUMN `pro_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `product` SET `pro_host_ts`=COALESCE(`pro_updated_date`,`pro_created_date`,UTC_TIMESTAMP(6)), `pro_local_ts`=COALESCE(`pro_updated_date`,`pro_created_date`,UTC_TIMESTAMP(6)), `pro_crud_value`=CASE WHEN `pro_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `product` MODIFY COLUMN `pro_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `pro_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `pro_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_category' AND column_name='pc_host_ts'), 'SELECT 1', 'ALTER TABLE `product_category` ADD COLUMN `pc_host_ts` DATETIME(6) NULL, ADD COLUMN `pc_local_ts` DATETIME(6) NULL, ADD COLUMN `pc_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `product_category` SET `pc_host_ts`=COALESCE(`pc_updated_date`,`pc_created_date`,UTC_TIMESTAMP(6)), `pc_local_ts`=COALESCE(`pc_updated_date`,`pc_created_date`,UTC_TIMESTAMP(6)), `pc_crud_value`=CASE WHEN `pc_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `product_category` MODIFY COLUMN `pc_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `pc_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `pc_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='attribute_definitions' AND column_name='ad_host_ts'), 'SELECT 1', 'ALTER TABLE `attribute_definitions` ADD COLUMN `ad_host_ts` DATETIME(6) NULL, ADD COLUMN `ad_local_ts` DATETIME(6) NULL, ADD COLUMN `ad_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `attribute_definitions` SET `ad_host_ts`=COALESCE(`ad_updated_date`,`ad_created_date`,UTC_TIMESTAMP(6)), `ad_local_ts`=COALESCE(`ad_updated_date`,`ad_created_date`,UTC_TIMESTAMP(6)), `ad_crud_value`=CASE WHEN `ad_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `attribute_definitions` MODIFY COLUMN `ad_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `ad_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `ad_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_attribute_values' AND column_name='pav_host_ts'), 'SELECT 1', 'ALTER TABLE `product_attribute_values` ADD COLUMN `pav_host_ts` DATETIME(6) NULL, ADD COLUMN `pav_local_ts` DATETIME(6) NULL, ADD COLUMN `pav_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `product_attribute_values` SET `pav_host_ts`=COALESCE(`pav_updated_date`,`pav_created_date`,UTC_TIMESTAMP(6)), `pav_local_ts`=COALESCE(`pav_updated_date`,`pav_created_date`,UTC_TIMESTAMP(6)), `pav_crud_value`=CASE WHEN `pav_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `product_attribute_values` MODIFY COLUMN `pav_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `pav_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `pav_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_configuration_revision' AND column_name='pcr_host_ts'), 'SELECT 1', 'ALTER TABLE `product_configuration_revision` ADD COLUMN `pcr_host_ts` DATETIME(6) NULL, ADD COLUMN `pcr_local_ts` DATETIME(6) NULL, ADD COLUMN `pcr_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `product_configuration_revision` SET `pcr_host_ts`=COALESCE(`pcr_created_date`,UTC_TIMESTAMP(6)), `pcr_local_ts`=COALESCE(`pcr_created_date`,UTC_TIMESTAMP(6)), `pcr_crud_value`='C';
ALTER TABLE `product_configuration_revision` MODIFY COLUMN `pcr_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `pcr_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `pcr_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_currency_config' AND column_name='pcc_host_ts'), 'SELECT 1', 'ALTER TABLE `product_currency_config` ADD COLUMN `pcc_host_ts` DATETIME(6) NULL, ADD COLUMN `pcc_local_ts` DATETIME(6) NULL, ADD COLUMN `pcc_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `product_currency_config` SET `pcc_host_ts`=COALESCE(`pcc_updated_date`,`pcc_created_date`,UTC_TIMESTAMP(6)), `pcc_local_ts`=COALESCE(`pcc_updated_date`,`pcc_created_date`,UTC_TIMESTAMP(6)), `pcc_crud_value`=CASE WHEN `pcc_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `product_currency_config` MODIFY COLUMN `pcc_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `pcc_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `pcc_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_role_config' AND column_name='prc_host_ts'), 'SELECT 1', 'ALTER TABLE `product_role_config` ADD COLUMN `prc_host_ts` DATETIME(6) NULL, ADD COLUMN `prc_local_ts` DATETIME(6) NULL, ADD COLUMN `prc_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `product_role_config` SET `prc_host_ts`=COALESCE(`prc_updated_date`,`prc_created_date`,UTC_TIMESTAMP(6)), `prc_local_ts`=COALESCE(`prc_updated_date`,`prc_created_date`,UTC_TIMESTAMP(6)), `prc_crud_value`=CASE WHEN `prc_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `product_role_config` MODIFY COLUMN `prc_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `prc_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `prc_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_rule' AND column_name='pr_host_ts'), 'SELECT 1', 'ALTER TABLE `product_rule` ADD COLUMN `pr_host_ts` DATETIME(6) NULL, ADD COLUMN `pr_local_ts` DATETIME(6) NULL, ADD COLUMN `pr_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `product_rule` SET `pr_host_ts`=COALESCE(`pr_updated_date`,`pr_created_date`,UTC_TIMESTAMP(6)), `pr_local_ts`=COALESCE(`pr_updated_date`,`pr_created_date`,UTC_TIMESTAMP(6)), `pr_crud_value`=CASE WHEN `pr_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `product_rule` MODIFY COLUMN `pr_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `pr_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `pr_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_condition' AND column_name='rc_host_ts'), 'SELECT 1', 'ALTER TABLE `rule_condition` ADD COLUMN `rc_host_ts` DATETIME(6) NULL, ADD COLUMN `rc_local_ts` DATETIME(6) NULL, ADD COLUMN `rc_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `rule_condition` SET `rc_host_ts`=COALESCE(`rc_updated_date`,`rc_created_date`,UTC_TIMESTAMP(6)), `rc_local_ts`=COALESCE(`rc_updated_date`,`rc_created_date`,UTC_TIMESTAMP(6)), `rc_crud_value`=CASE WHEN `rc_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `rule_condition` MODIFY COLUMN `rc_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `rc_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `rc_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_outcome' AND column_name='ro_host_ts'), 'SELECT 1', 'ALTER TABLE `rule_outcome` ADD COLUMN `ro_host_ts` DATETIME(6) NULL, ADD COLUMN `ro_local_ts` DATETIME(6) NULL, ADD COLUMN `ro_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `rule_outcome` SET `ro_host_ts`=COALESCE(`ro_updated_date`,`ro_created_date`,UTC_TIMESTAMP(6)), `ro_local_ts`=COALESCE(`ro_updated_date`,`ro_created_date`,UTC_TIMESTAMP(6)), `ro_crud_value`=CASE WHEN `ro_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `rule_outcome` MODIFY COLUMN `ro_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `ro_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `ro_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rule_type' AND column_name='rut_host_ts'), 'SELECT 1', 'ALTER TABLE `rule_type` ADD COLUMN `rut_host_ts` DATETIME(6) NULL, ADD COLUMN `rut_local_ts` DATETIME(6) NULL, ADD COLUMN `rut_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `rule_type` SET `rut_host_ts`=COALESCE(`rut_updated_date`,`rut_created_date`,UTC_TIMESTAMP(6)), `rut_local_ts`=COALESCE(`rut_updated_date`,`rut_created_date`,UTC_TIMESTAMP(6)), `rut_crud_value`=CASE WHEN `rut_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `rule_type` MODIFY COLUMN `rut_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `rut_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `rut_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='role_type' AND column_name='rot_host_ts'), 'SELECT 1', 'ALTER TABLE `role_type` ADD COLUMN `rot_host_ts` DATETIME(6) NULL, ADD COLUMN `rot_local_ts` DATETIME(6) NULL, ADD COLUMN `rot_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `role_type` SET `rot_host_ts`=COALESCE(`rot_updated_date`,`rot_created_date`,UTC_TIMESTAMP(6)), `rot_local_ts`=COALESCE(`rot_updated_date`,`rot_created_date`,UTC_TIMESTAMP(6)), `rot_crud_value`=CASE WHEN `rot_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `role_type` MODIFY COLUMN `rot_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `rot_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `rot_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='charge_config' AND column_name='cc_host_ts'), 'SELECT 1', 'ALTER TABLE `charge_config` ADD COLUMN `cc_host_ts` DATETIME(6) NULL, ADD COLUMN `cc_local_ts` DATETIME(6) NULL, ADD COLUMN `cc_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `charge_config` SET `cc_host_ts`=COALESCE(`cc_updated_date`,`cc_created_date`,UTC_TIMESTAMP(6)), `cc_local_ts`=COALESCE(`cc_updated_date`,`cc_created_date`,UTC_TIMESTAMP(6)), `cc_crud_value`=CASE WHEN `cc_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `charge_config` MODIFY COLUMN `cc_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cc_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cc_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='charge_slab' AND column_name='cs_host_ts'), 'SELECT 1', 'ALTER TABLE `charge_slab` ADD COLUMN `cs_host_ts` DATETIME(6) NULL, ADD COLUMN `cs_local_ts` DATETIME(6) NULL, ADD COLUMN `cs_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `charge_slab` SET `cs_host_ts`=COALESCE(`cs_updated_date`,`cs_created_date`,UTC_TIMESTAMP(6)), `cs_local_ts`=COALESCE(`cs_updated_date`,`cs_created_date`,UTC_TIMESTAMP(6)), `cs_crud_value`=CASE WHEN `cs_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `charge_slab` MODIFY COLUMN `cs_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cs_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cs_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='currency' AND column_name='cur_host_ts'), 'SELECT 1', 'ALTER TABLE `currency` ADD COLUMN `cur_host_ts` DATETIME(6) NULL, ADD COLUMN `cur_local_ts` DATETIME(6) NULL, ADD COLUMN `cur_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `currency` SET `cur_host_ts`=COALESCE(`cur_updated_date`,`cur_created_date`,UTC_TIMESTAMP(6)), `cur_local_ts`=COALESCE(`cur_updated_date`,`cur_created_date`,UTC_TIMESTAMP(6)), `cur_crud_value`=CASE WHEN `cur_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `currency` MODIFY COLUMN `cur_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cur_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `cur_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sweep_config' AND column_name='sc_host_ts'), 'SELECT 1', 'ALTER TABLE `sweep_config` ADD COLUMN `sc_host_ts` DATETIME(6) NULL, ADD COLUMN `sc_local_ts` DATETIME(6) NULL, ADD COLUMN `sc_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `sweep_config` SET `sc_host_ts`=COALESCE(`sc_updated_date`,`sc_created_date`,UTC_TIMESTAMP(6)), `sc_local_ts`=COALESCE(`sc_updated_date`,`sc_created_date`,UTC_TIMESTAMP(6)), `sc_crud_value`=CASE WHEN `sc_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `sweep_config` MODIFY COLUMN `sc_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `sc_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `sc_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gl_code_mapping' AND column_name='gcm_host_ts'), 'SELECT 1', 'ALTER TABLE `gl_code_mapping` ADD COLUMN `gcm_host_ts` DATETIME(6) NULL, ADD COLUMN `gcm_local_ts` DATETIME(6) NULL, ADD COLUMN `gcm_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `gl_code_mapping` SET `gcm_host_ts`=COALESCE(`gcm_updated_date`,`gcm_created_date`,UTC_TIMESTAMP(6)), `gcm_local_ts`=COALESCE(`gcm_updated_date`,`gcm_created_date`,UTC_TIMESTAMP(6)), `gcm_crud_value`=CASE WHEN `gcm_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `gl_code_mapping` MODIFY COLUMN `gcm_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `gcm_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `gcm_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';
