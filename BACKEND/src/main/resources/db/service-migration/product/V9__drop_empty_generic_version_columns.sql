-- Remove only empty generic VARCHAR version columns from moneypal_product.
-- Each column is counted immediately before its conditional DROP; populated columns are retained.
-- Numeric row/event/source versions and business policy versions are not candidates.

-- attribute_definitions.ad_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'attribute_definitions' AND column_name = 'ad_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `attribute_definitions` WHERE `ad_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `attribute_definitions` DROP COLUMN `ad_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- charge_config.cc_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'charge_config' AND column_name = 'cc_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `charge_config` WHERE `cc_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `charge_config` DROP COLUMN `cc_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- charge_slab.cs_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'charge_slab' AND column_name = 'cs_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `charge_slab` WHERE `cs_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `charge_slab` DROP COLUMN `cs_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- currency.cur_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'currency' AND column_name = 'cur_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `currency` WHERE `cur_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `currency` DROP COLUMN `cur_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- gl_code_mapping.gcm_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'gl_code_mapping' AND column_name = 'gcm_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `gl_code_mapping` WHERE `gcm_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `gl_code_mapping` DROP COLUMN `gcm_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- product.pro_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'product' AND column_name = 'pro_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `product` WHERE `pro_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `product` DROP COLUMN `pro_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- product_attribute_values.pav_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'product_attribute_values' AND column_name = 'pav_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `product_attribute_values` WHERE `pav_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `product_attribute_values` DROP COLUMN `pav_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- product_category.pc_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'product_category' AND column_name = 'pc_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `product_category` WHERE `pc_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `product_category` DROP COLUMN `pc_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- product_currency_config.pcc_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'product_currency_config' AND column_name = 'pcc_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `product_currency_config` WHERE `pcc_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `product_currency_config` DROP COLUMN `pcc_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- product_role_config.prc_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'product_role_config' AND column_name = 'prc_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `product_role_config` WHERE `prc_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `product_role_config` DROP COLUMN `prc_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- product_rule.pr_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'product_rule' AND column_name = 'pr_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `product_rule` WHERE `pr_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `product_rule` DROP COLUMN `pr_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- role_type.rot_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'role_type' AND column_name = 'rot_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `role_type` WHERE `rot_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `role_type` DROP COLUMN `rot_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- rule_condition.rc_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'rule_condition' AND column_name = 'rc_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `rule_condition` WHERE `rc_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `rule_condition` DROP COLUMN `rc_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- rule_outcome.ro_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'rule_outcome' AND column_name = 'ro_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `rule_outcome` WHERE `ro_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `rule_outcome` DROP COLUMN `ro_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- rule_type.rut_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'rule_type' AND column_name = 'rut_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `rule_type` WHERE `rut_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `rule_type` DROP COLUMN `rut_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;

-- sweep_config.sc_version
SET @version_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'sweep_config' AND column_name = 'sc_version'),
    'SELECT COUNT(*) INTO @version_non_null_count FROM `sweep_config` WHERE `sc_version` IS NOT NULL',
    'SET @version_non_null_count = 1'
);
PREPARE version_count_stmt FROM @version_count_sql;
EXECUTE version_count_stmt;
DEALLOCATE PREPARE version_count_stmt;
SET @version_drop_sql = IF(@version_non_null_count = 0,
    'ALTER TABLE `sweep_config` DROP COLUMN `sc_version`',
    'SET @version_skip = 1'
);
PREPARE version_drop_stmt FROM @version_drop_sql;
EXECUTE version_drop_stmt;
DEALLOCATE PREPARE version_drop_stmt;
