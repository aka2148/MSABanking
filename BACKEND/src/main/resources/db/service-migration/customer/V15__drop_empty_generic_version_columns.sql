-- Remove only empty generic VARCHAR version columns from moneypal_customer.
-- Each column is counted immediately before its conditional DROP; populated columns are retained.
-- Numeric row/event/source versions and business policy versions are not candidates.

-- branches.bra_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'branches' AND column_name = 'bra_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `branches` WHERE `bra_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `branches` DROP COLUMN `bra_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- customer_addresses.cad_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'customer_addresses' AND column_name = 'cad_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `customer_addresses` WHERE `cad_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `customer_addresses` DROP COLUMN `cad_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- customer_classification_types.cct_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'customer_classification_types' AND column_name = 'cct_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `customer_classification_types` WHERE `cct_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `customer_classification_types` DROP COLUMN `cct_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- customer_details.cud_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'customer_details' AND column_name = 'cud_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `customer_details` WHERE `cud_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `customer_details` DROP COLUMN `cud_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- customer_emails.cem_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'customer_emails' AND column_name = 'cem_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `customer_emails` WHERE `cem_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `customer_emails` DROP COLUMN `cem_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- customer_identity_proofs.cip_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'customer_identity_proofs' AND column_name = 'cip_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `customer_identity_proofs` WHERE `cip_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `customer_identity_proofs` DROP COLUMN `cip_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- customer_phone_numbers.cph_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'customer_phone_numbers' AND column_name = 'cph_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `customer_phone_numbers` WHERE `cph_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `customer_phone_numbers` DROP COLUMN `cph_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- customers.cus_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'customers' AND column_name = 'cus_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `customers` WHERE `cus_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `customers` DROP COLUMN `cus_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- employee_profile.emp_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'employee_profile' AND column_name = 'emp_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `employee_profile` WHERE `emp_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `employee_profile` DROP COLUMN `emp_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- roles.rol_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'roles' AND column_name = 'rol_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `roles` WHERE `rol_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `roles` DROP COLUMN `rol_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- user_credentials.ucr_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'user_credentials' AND column_name = 'ucr_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `user_credentials` WHERE `ucr_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `user_credentials` DROP COLUMN `ucr_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- user_password_history.uph_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'user_password_history' AND column_name = 'uph_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `user_password_history` WHERE `uph_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `user_password_history` DROP COLUMN `uph_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- user_roles.ur_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'user_roles' AND column_name = 'ur_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `user_roles` WHERE `ur_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `user_roles` DROP COLUMN `ur_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- users.usr_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'usr_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `users` WHERE `usr_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `users` DROP COLUMN `usr_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;
