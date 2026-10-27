-- Remove only empty generic VARCHAR version columns from moneypal_fd.
-- Each column is counted immediately before its conditional DROP; populated columns are retained.
-- Numeric row/event/source versions and business policy versions are not candidates.

-- account_holder_roles.ahr_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'account_holder_roles' AND column_name = 'ahr_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `account_holder_roles` WHERE `ahr_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `account_holder_roles` DROP COLUMN `ahr_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- fd_account_events.fae_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'fd_account_events' AND column_name = 'fae_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `fd_account_events` WHERE `fae_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `fd_account_events` DROP COLUMN `fae_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- fd_accounts.fa_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'fd_accounts' AND column_name = 'fa_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `fd_accounts` WHERE `fa_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `fd_accounts` DROP COLUMN `fa_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- fd_currency_ref.fcr_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'fd_currency_ref' AND column_name = 'fcr_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `fd_currency_ref` WHERE `fcr_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `fd_currency_ref` DROP COLUMN `fcr_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- fd_interest_accrual.fia_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'fd_interest_accrual' AND column_name = 'fia_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `fd_interest_accrual` WHERE `fia_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `fd_interest_accrual` DROP COLUMN `fia_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- fd_interest_payout.fip_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'fd_interest_payout' AND column_name = 'fip_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `fd_interest_payout` WHERE `fip_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `fd_interest_payout` DROP COLUMN `fip_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- fd_maturity_instructions.fmi_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'fd_maturity_instructions' AND column_name = 'fmi_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `fd_maturity_instructions` WHERE `fmi_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `fd_maturity_instructions` DROP COLUMN `fmi_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- fd_notices.fn_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'fd_notices' AND column_name = 'fn_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `fd_notices` WHERE `fn_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `fd_notices` DROP COLUMN `fn_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- fd_payout_accounts.fpa_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'fd_payout_accounts' AND column_name = 'fpa_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `fd_payout_accounts` WHERE `fpa_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `fd_payout_accounts` DROP COLUMN `fpa_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- fd_premature_closure.fpc_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'fd_premature_closure' AND column_name = 'fpc_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `fd_premature_closure` WHERE `fpc_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `fd_premature_closure` DROP COLUMN `fpc_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- fd_product_ref.fpr_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'fd_product_ref' AND column_name = 'fpr_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `fd_product_ref` WHERE `fpr_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `fd_product_ref` DROP COLUMN `fpr_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- fd_role_type_ref.frtr_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'fd_role_type_ref' AND column_name = 'frtr_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `fd_role_type_ref` WHERE `frtr_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `fd_role_type_ref` DROP COLUMN `frtr_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- fd_rule_outcome_ref.fror_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'fd_rule_outcome_ref' AND column_name = 'fror_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `fd_rule_outcome_ref` WHERE `fror_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `fd_rule_outcome_ref` DROP COLUMN `fror_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- fd_statements.fs_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'fd_statements' AND column_name = 'fs_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `fd_statements` WHERE `fs_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `fd_statements` DROP COLUMN `fs_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- fd_transactions.ft_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'fd_transactions' AND column_name = 'ft_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `fd_transactions` WHERE `ft_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `fd_transactions` DROP COLUMN `ft_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- fd_user_ref.fur_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'fd_user_ref' AND column_name = 'fur_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `fd_user_ref` WHERE `fur_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `fd_user_ref` DROP COLUMN `fur_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- transaction_types.tt_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'transaction_types' AND column_name = 'tt_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `transaction_types` WHERE `tt_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `transaction_types` DROP COLUMN `tt_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;
