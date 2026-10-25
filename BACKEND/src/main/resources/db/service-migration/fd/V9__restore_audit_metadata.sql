-- Restores the legacy host/local/CRUD audit metadata for this MySQL service.
-- MySQL initializes and refreshes both UTC timestamps; CRUD defaults to C on insert.
-- Column defaults and ON UPDATE clauses cover all application and direct SQL write paths.

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='fd_accounts' AND column_name='fa_host_ts'), 'SELECT 1', 'ALTER TABLE `fd_accounts` ADD COLUMN `fa_host_ts` DATETIME(6) NULL, ADD COLUMN `fa_local_ts` DATETIME(6) NULL, ADD COLUMN `fa_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `fd_accounts` SET `fa_host_ts`=COALESCE(`fa_updated_date`,`fa_created_date`,UTC_TIMESTAMP(6)), `fa_local_ts`=COALESCE(`fa_updated_date`,`fa_created_date`,UTC_TIMESTAMP(6)), `fa_crud_value`=CASE WHEN `fa_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `fd_accounts` MODIFY COLUMN `fa_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fa_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fa_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='fd_interest_accrual' AND column_name='fia_host_ts'), 'SELECT 1', 'ALTER TABLE `fd_interest_accrual` ADD COLUMN `fia_host_ts` DATETIME(6) NULL, ADD COLUMN `fia_local_ts` DATETIME(6) NULL, ADD COLUMN `fia_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `fd_interest_accrual` SET `fia_host_ts`=COALESCE(`fia_updated_date`,`fia_created_date`,UTC_TIMESTAMP(6)), `fia_local_ts`=COALESCE(`fia_updated_date`,`fia_created_date`,UTC_TIMESTAMP(6)), `fia_crud_value`=CASE WHEN `fia_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `fd_interest_accrual` MODIFY COLUMN `fia_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fia_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fia_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='fd_interest_payout' AND column_name='fip_host_ts'), 'SELECT 1', 'ALTER TABLE `fd_interest_payout` ADD COLUMN `fip_host_ts` DATETIME(6) NULL, ADD COLUMN `fip_local_ts` DATETIME(6) NULL, ADD COLUMN `fip_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `fd_interest_payout` SET `fip_host_ts`=COALESCE(`fip_updated_date`,`fip_created_date`,UTC_TIMESTAMP(6)), `fip_local_ts`=COALESCE(`fip_updated_date`,`fip_created_date`,UTC_TIMESTAMP(6)), `fip_crud_value`=CASE WHEN `fip_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `fd_interest_payout` MODIFY COLUMN `fip_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fip_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fip_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='fd_premature_closure' AND column_name='fpc_host_ts'), 'SELECT 1', 'ALTER TABLE `fd_premature_closure` ADD COLUMN `fpc_host_ts` DATETIME(6) NULL, ADD COLUMN `fpc_local_ts` DATETIME(6) NULL, ADD COLUMN `fpc_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `fd_premature_closure` SET `fpc_host_ts`=COALESCE(`fpc_updated_date`,`fpc_created_date`,UTC_TIMESTAMP(6)), `fpc_local_ts`=COALESCE(`fpc_updated_date`,`fpc_created_date`,UTC_TIMESTAMP(6)), `fpc_crud_value`=CASE WHEN `fpc_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `fd_premature_closure` MODIFY COLUMN `fpc_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fpc_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fpc_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='fd_maturity_instructions' AND column_name='fmi_host_ts'), 'SELECT 1', 'ALTER TABLE `fd_maturity_instructions` ADD COLUMN `fmi_host_ts` DATETIME(6) NULL, ADD COLUMN `fmi_local_ts` DATETIME(6) NULL, ADD COLUMN `fmi_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `fd_maturity_instructions` SET `fmi_host_ts`=COALESCE(`fmi_updated_date`,`fmi_created_date`,UTC_TIMESTAMP(6)), `fmi_local_ts`=COALESCE(`fmi_updated_date`,`fmi_created_date`,UTC_TIMESTAMP(6)), `fmi_crud_value`=CASE WHEN `fmi_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `fd_maturity_instructions` MODIFY COLUMN `fmi_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fmi_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fmi_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='fd_payout_accounts' AND column_name='fpa_host_ts'), 'SELECT 1', 'ALTER TABLE `fd_payout_accounts` ADD COLUMN `fpa_host_ts` DATETIME(6) NULL, ADD COLUMN `fpa_local_ts` DATETIME(6) NULL, ADD COLUMN `fpa_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `fd_payout_accounts` SET `fpa_host_ts`=COALESCE(`fpa_updated_date`,`fpa_created_date`,UTC_TIMESTAMP(6)), `fpa_local_ts`=COALESCE(`fpa_updated_date`,`fpa_created_date`,UTC_TIMESTAMP(6)), `fpa_crud_value`=CASE WHEN `fpa_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `fd_payout_accounts` MODIFY COLUMN `fpa_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fpa_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fpa_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='fd_notices' AND column_name='fn_host_ts'), 'SELECT 1', 'ALTER TABLE `fd_notices` ADD COLUMN `fn_host_ts` DATETIME(6) NULL, ADD COLUMN `fn_local_ts` DATETIME(6) NULL, ADD COLUMN `fn_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `fd_notices` SET `fn_host_ts`=COALESCE(`fn_updated_date`,`fn_created_date`,UTC_TIMESTAMP(6)), `fn_local_ts`=COALESCE(`fn_updated_date`,`fn_created_date`,UTC_TIMESTAMP(6)), `fn_crud_value`=CASE WHEN `fn_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `fd_notices` MODIFY COLUMN `fn_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fn_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fn_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='fd_statements' AND column_name='fs_host_ts'), 'SELECT 1', 'ALTER TABLE `fd_statements` ADD COLUMN `fs_host_ts` DATETIME(6) NULL, ADD COLUMN `fs_local_ts` DATETIME(6) NULL, ADD COLUMN `fs_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `fd_statements` SET `fs_host_ts`=COALESCE(`fs_updated_date`,`fs_created_date`,UTC_TIMESTAMP(6)), `fs_local_ts`=COALESCE(`fs_updated_date`,`fs_created_date`,UTC_TIMESTAMP(6)), `fs_crud_value`=CASE WHEN `fs_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `fd_statements` MODIFY COLUMN `fs_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fs_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fs_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='fd_transactions' AND column_name='ft_host_ts'), 'SELECT 1', 'ALTER TABLE `fd_transactions` ADD COLUMN `ft_host_ts` DATETIME(6) NULL, ADD COLUMN `ft_local_ts` DATETIME(6) NULL, ADD COLUMN `ft_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `fd_transactions` SET `ft_host_ts`=COALESCE(`ft_updated_date`,`ft_created_date`,UTC_TIMESTAMP(6)), `ft_local_ts`=COALESCE(`ft_updated_date`,`ft_created_date`,UTC_TIMESTAMP(6)), `ft_crud_value`=CASE WHEN `ft_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `fd_transactions` MODIFY COLUMN `ft_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `ft_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `ft_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='fd_account_events' AND column_name='fae_host_ts'), 'SELECT 1', 'ALTER TABLE `fd_account_events` ADD COLUMN `fae_host_ts` DATETIME(6) NULL, ADD COLUMN `fae_local_ts` DATETIME(6) NULL, ADD COLUMN `fae_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `fd_account_events` SET `fae_host_ts`=COALESCE(`fae_updated_date`,`fae_created_date`,UTC_TIMESTAMP(6)), `fae_local_ts`=COALESCE(`fae_updated_date`,`fae_created_date`,UTC_TIMESTAMP(6)), `fae_crud_value`=CASE WHEN `fae_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `fd_account_events` MODIFY COLUMN `fae_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fae_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fae_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='account_holder_roles' AND column_name='ahr_host_ts'), 'SELECT 1', 'ALTER TABLE `account_holder_roles` ADD COLUMN `ahr_host_ts` DATETIME(6) NULL, ADD COLUMN `ahr_local_ts` DATETIME(6) NULL, ADD COLUMN `ahr_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `account_holder_roles` SET `ahr_host_ts`=COALESCE(`ahr_updated_date`,`ahr_created_date`,UTC_TIMESTAMP(6)), `ahr_local_ts`=COALESCE(`ahr_updated_date`,`ahr_created_date`,UTC_TIMESTAMP(6)), `ahr_crud_value`=CASE WHEN `ahr_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `account_holder_roles` MODIFY COLUMN `ahr_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `ahr_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `ahr_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='transaction_types' AND column_name='tt_host_ts'), 'SELECT 1', 'ALTER TABLE `transaction_types` ADD COLUMN `tt_host_ts` DATETIME(6) NULL, ADD COLUMN `tt_local_ts` DATETIME(6) NULL, ADD COLUMN `tt_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `transaction_types` SET `tt_host_ts`=COALESCE(`tt_updated_date`,`tt_created_date`,UTC_TIMESTAMP(6)), `tt_local_ts`=COALESCE(`tt_updated_date`,`tt_created_date`,UTC_TIMESTAMP(6)), `tt_crud_value`=CASE WHEN `tt_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `transaction_types` MODIFY COLUMN `tt_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `tt_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `tt_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='fd_product_ref' AND column_name='fpr_host_ts'), 'SELECT 1', 'ALTER TABLE `fd_product_ref` ADD COLUMN `fpr_host_ts` DATETIME(6) NULL, ADD COLUMN `fpr_local_ts` DATETIME(6) NULL, ADD COLUMN `fpr_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `fd_product_ref` SET `fpr_host_ts`=COALESCE(`fpr_updated_date`,`fpr_created_date`,UTC_TIMESTAMP(6)), `fpr_local_ts`=COALESCE(`fpr_updated_date`,`fpr_created_date`,UTC_TIMESTAMP(6)), `fpr_crud_value`=CASE WHEN `fpr_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `fd_product_ref` MODIFY COLUMN `fpr_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fpr_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fpr_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='fd_user_ref' AND column_name='fur_host_ts'), 'SELECT 1', 'ALTER TABLE `fd_user_ref` ADD COLUMN `fur_host_ts` DATETIME(6) NULL, ADD COLUMN `fur_local_ts` DATETIME(6) NULL, ADD COLUMN `fur_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `fd_user_ref` SET `fur_host_ts`=COALESCE(`fur_updated_date`,`fur_created_date`,UTC_TIMESTAMP(6)), `fur_local_ts`=COALESCE(`fur_updated_date`,`fur_created_date`,UTC_TIMESTAMP(6)), `fur_crud_value`=CASE WHEN `fur_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `fd_user_ref` MODIFY COLUMN `fur_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fur_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fur_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='fd_currency_ref' AND column_name='fcr_host_ts'), 'SELECT 1', 'ALTER TABLE `fd_currency_ref` ADD COLUMN `fcr_host_ts` DATETIME(6) NULL, ADD COLUMN `fcr_local_ts` DATETIME(6) NULL, ADD COLUMN `fcr_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `fd_currency_ref` SET `fcr_host_ts`=COALESCE(`fcr_updated_date`,`fcr_created_date`,UTC_TIMESTAMP(6)), `fcr_local_ts`=COALESCE(`fcr_updated_date`,`fcr_created_date`,UTC_TIMESTAMP(6)), `fcr_crud_value`=CASE WHEN `fcr_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `fd_currency_ref` MODIFY COLUMN `fcr_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fcr_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fcr_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='fd_rule_outcome_ref' AND column_name='fror_host_ts'), 'SELECT 1', 'ALTER TABLE `fd_rule_outcome_ref` ADD COLUMN `fror_host_ts` DATETIME(6) NULL, ADD COLUMN `fror_local_ts` DATETIME(6) NULL, ADD COLUMN `fror_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `fd_rule_outcome_ref` SET `fror_host_ts`=COALESCE(`fror_updated_date`,`fror_created_date`,UTC_TIMESTAMP(6)), `fror_local_ts`=COALESCE(`fror_updated_date`,`fror_created_date`,UTC_TIMESTAMP(6)), `fror_crud_value`=CASE WHEN `fror_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `fd_rule_outcome_ref` MODIFY COLUMN `fror_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fror_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `fror_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';

SET @audit_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='fd_role_type_ref' AND column_name='frtr_host_ts'), 'SELECT 1', 'ALTER TABLE `fd_role_type_ref` ADD COLUMN `frtr_host_ts` DATETIME(6) NULL, ADD COLUMN `frtr_local_ts` DATETIME(6) NULL, ADD COLUMN `frtr_crud_value` VARCHAR(10) NULL');
PREPARE audit_stmt FROM @audit_ddl;
EXECUTE audit_stmt;
DEALLOCATE PREPARE audit_stmt;
UPDATE `fd_role_type_ref` SET `frtr_host_ts`=COALESCE(`frtr_updated_date`,`frtr_created_date`,UTC_TIMESTAMP(6)), `frtr_local_ts`=COALESCE(`frtr_updated_date`,`frtr_created_date`,UTC_TIMESTAMP(6)), `frtr_crud_value`=CASE WHEN `frtr_updated_date` IS NULL THEN 'C' ELSE 'U' END;
ALTER TABLE `fd_role_type_ref` MODIFY COLUMN `frtr_host_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `frtr_local_ts` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), MODIFY COLUMN `frtr_crud_value` VARCHAR(10) NOT NULL DEFAULT 'C';
