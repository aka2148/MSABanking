-- MoneyPal college project: product; MySQL 8.4 / MySQL 8.0.16+.
-- FRESH EMPTY DATABASE ONLY; no data import or destructive DROP statements.
-- Run directly in MySQL Workbench as an admin.
-- Flyway alternative: create DB first, remove CREATE DATABASE and USE below,
-- rename to V1__init.sql in THIS service's db/migration folder.
-- Do not manually import and also run Flyway V1 on the same tables.
-- Removed *_ldbid, *_host_ts, *_local_ts, *_rule_system_id,
-- *_crud_value, *_workstation_id. Remove these JPA mappings too.
-- Kept existing business columns, UUIDs, versions, created/updated actor/date fields.
-- No audit triggers; application supplies actor/update timestamps.
-- Java IDs: Long; generated owner PKs: IDENTITY; replica PKs are assigned source IDs.
-- DATETIME values are UTC; configure application/JDBC connections to UTC too.
-- Kafka still requires Spring producers/consumers; SQL does not connect it automatically.
USE moneypal_product;

CREATE TABLE `product` (
    `pro_product_id` BIGINT NOT NULL AUTO_INCREMENT,
    `pro_product_code` VARCHAR(50) NOT NULL,
    `pro_product_name` VARCHAR(255) NOT NULL,
    `pro_category_id` BIGINT NOT NULL,
    `pro_base_currency_code` VARCHAR(10),
    `pro_status` VARCHAR(30) DEFAULT 'DRAFT' NOT NULL,
    `pro_effective_from` VARCHAR(30),
    `pro_effective_to` VARCHAR(30),
    `pro_description` VARCHAR(1000),
    `pro_created_by` VARCHAR(255),
    `pro_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `pro_updated_by` VARCHAR(255),
    `pro_updated_date` DATETIME(6),
    `pro_version` VARCHAR(50),
    `pro_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    pro_event_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT `product_pkey` PRIMARY KEY (`pro_product_id`),
    CONSTRAINT `product_pro_product_code_key` UNIQUE (`pro_product_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `product_category` (
    `pc_category_id` BIGINT NOT NULL AUTO_INCREMENT,
    `pc_category_code` VARCHAR(50) NOT NULL,
    `pc_category_name` VARCHAR(255) NOT NULL,
    `pc_parent_category_id` BIGINT,
    `pc_created_by` VARCHAR(255),
    `pc_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `pc_updated_by` VARCHAR(255),
    `pc_updated_date` DATETIME(6),
    `pc_version` VARCHAR(50),
    `pc_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `product_category_pc_category_code_key` UNIQUE (`pc_category_code`),
    CONSTRAINT `product_category_pkey` PRIMARY KEY (`pc_category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `attribute_definitions` (
    `ad_attribute_def_id` BIGINT NOT NULL AUTO_INCREMENT,
    `ad_category_id` BIGINT NOT NULL,
    `ad_attribute_key` VARCHAR(100) NOT NULL,
    `ad_data_type` VARCHAR(30) NOT NULL,
    `ad_display_name` VARCHAR(255),
    `ad_is_required` TINYINT(1) DEFAULT false NOT NULL,
    `ad_created_by` VARCHAR(255),
    `ad_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `ad_updated_by` VARCHAR(255),
    `ad_updated_date` DATETIME(6),
    `ad_version` VARCHAR(50),
    `ad_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    `ad_choice_values` VARCHAR(1000),
    `ad_unit` VARCHAR(30),
    `ad_default_value` VARCHAR(255),
    `ad_min_value` DECIMAL(22,8),
    `ad_max_value` DECIMAL(22,8),
    `ad_display_order` INT DEFAULT 0 NOT NULL,
    `ad_behavior_key` VARCHAR(50),
    CONSTRAINT `attribute_definitions_pkey` PRIMARY KEY (`ad_attribute_def_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `product_attribute_values` (
    `pav_value_id` BIGINT NOT NULL AUTO_INCREMENT,
    `pav_product_id` BIGINT NOT NULL,
    `pav_attribute_def_id` BIGINT NOT NULL,
    `pav_value_string` VARCHAR(500),
    `pav_value_number` DECIMAL(18,4),
    `pav_value_date` DATETIME(6),
    `pav_created_by` VARCHAR(255),
    `pav_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `pav_updated_by` VARCHAR(255),
    `pav_updated_date` DATETIME(6),
    `pav_version` VARCHAR(50),
    `pav_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `product_attribute_values_pkey` PRIMARY KEY (`pav_value_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `product_configuration_revision` (
    `pcr_revision_id` BIGINT NOT NULL AUTO_INCREMENT,
    `pcr_product_id` BIGINT NOT NULL,
    `pcr_revision_no` INT NOT NULL,
    `pcr_status` VARCHAR(20) NOT NULL,
    `pcr_snapshot_json` JSON NOT NULL,
    `pcr_created_by` VARCHAR(100) NOT NULL,
    `pcr_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    CONSTRAINT `ck_product_configuration_revision_status` CHECK (((`pcr_status`) IN ('DRAFT', 'ACTIVE'))),
    CONSTRAINT `product_configuration_revision_pkey` PRIMARY KEY (`pcr_revision_id`),
    CONSTRAINT `uq_product_configuration_revision` UNIQUE (`pcr_product_id`, `pcr_revision_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `product_currency_config` (
    `pcc_config_id` BIGINT NOT NULL AUTO_INCREMENT,
    `pcc_product_id` BIGINT NOT NULL,
    `pcc_currency_id` BIGINT NOT NULL,
    `pcc_is_base_currency` TINYINT(1) DEFAULT false NOT NULL,
    `pcc_wallet_enabled` TINYINT(1) DEFAULT false NOT NULL,
    `pcc_created_by` VARCHAR(255),
    `pcc_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `pcc_updated_by` VARCHAR(255),
    `pcc_updated_date` DATETIME(6),
    `pcc_version` VARCHAR(50),
    `pcc_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    `pcc_minimum_amount` DECIMAL(22,8),
    `pcc_maximum_amount` DECIMAL(22,8),
    CONSTRAINT `ck_product_currency_config_amount_limits` CHECK ((((`pcc_minimum_amount` IS NULL) AND (`pcc_maximum_amount` IS NULL)) OR ((`pcc_minimum_amount` >= (0)) AND (`pcc_maximum_amount` > (0)) AND (`pcc_minimum_amount` <= `pcc_maximum_amount`)))),
    pcc_base_product_id BIGINT GENERATED ALWAYS AS (CASE WHEN pcc_is_base_currency = 1 THEN pcc_product_id ELSE NULL END) STORED,
    CONSTRAINT `product_currency_config_pkey` PRIMARY KEY (`pcc_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `product_role_config` (
    `prc_id` BIGINT NOT NULL AUTO_INCREMENT,
    `prc_product_id` BIGINT NOT NULL,
    `prc_role_type_id` BIGINT NOT NULL,
    `prc_is_mandatory` TINYINT(1) DEFAULT false NOT NULL,
    `prc_min_count` INT,
    `prc_max_count` INT,
    `prc_created_by` VARCHAR(255),
    `prc_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `prc_updated_by` VARCHAR(255),
    `prc_updated_date` DATETIME(6),
    `prc_version` VARCHAR(50),
    `prc_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `product_role_config_pkey` PRIMARY KEY (`prc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `product_rule` (
    `pr_rule_id` BIGINT NOT NULL AUTO_INCREMENT,
    `pr_product_id` BIGINT NOT NULL,
    `pr_rule_type_id` BIGINT NOT NULL,
    `pr_rule_name` VARCHAR(255) NOT NULL,
    `pr_priority` INT,
    `pr_combine_logic` VARCHAR(30),
    `pr_is_active` TINYINT(1) DEFAULT true NOT NULL,
    `pr_effective_from` DATETIME(6),
    `pr_effective_to` DATETIME(6),
    `pr_created_by` VARCHAR(255),
    `pr_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `pr_updated_by` VARCHAR(255),
    `pr_updated_date` DATETIME(6),
    `pr_version` VARCHAR(50),
    `pr_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `product_rule_pkey` PRIMARY KEY (`pr_rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `rule_condition` (
    `rc_condition_id` BIGINT NOT NULL AUTO_INCREMENT,
    `rc_rule_id` BIGINT NOT NULL,
    `rc_condition_attribute` VARCHAR(100),
    `rc_operator` VARCHAR(30),
    `rc_value_from` VARCHAR(100),
    `rc_value_to` VARCHAR(100),
    `rc_value_set` VARCHAR(500),
    `rc_created_by` VARCHAR(255),
    `rc_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `rc_updated_by` VARCHAR(255),
    `rc_updated_date` DATETIME(6),
    `rc_version` VARCHAR(50),
    `rc_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `rule_condition_pkey` PRIMARY KEY (`rc_condition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `rule_outcome` (
    `ro_outcome_id` BIGINT NOT NULL AUTO_INCREMENT,
    `ro_rule_id` BIGINT NOT NULL,
    `ro_outcome_type` VARCHAR(30),
    `ro_outcome_value` DECIMAL(22,8),
    `ro_unit` VARCHAR(30),
    `ro_compounding_basis` VARCHAR(30),
    `ro_payout_frequency` VARCHAR(30),
    `ro_currency_id` BIGINT,
    `ro_created_by` VARCHAR(255),
    `ro_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `ro_updated_by` VARCHAR(255),
    `ro_updated_date` DATETIME(6),
    `ro_version` VARCHAR(50),
    `ro_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `rule_outcome_pkey` PRIMARY KEY (`ro_outcome_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `rule_type` (
    `rut_rule_type_id` BIGINT NOT NULL AUTO_INCREMENT,
    `rut_rule_type_code` VARCHAR(50) NOT NULL,
    `rut_description` VARCHAR(500),
    `rut_created_by` VARCHAR(255),
    `rut_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `rut_updated_by` VARCHAR(255),
    `rut_updated_date` DATETIME(6),
    `rut_version` VARCHAR(50),
    `rut_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `rule_type_pkey` PRIMARY KEY (`rut_rule_type_id`),
    CONSTRAINT `rule_type_rut_rule_type_code_key` UNIQUE (`rut_rule_type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `role_type` (
    `rot_role_type_id` BIGINT NOT NULL AUTO_INCREMENT,
    `rot_role_code` VARCHAR(50) NOT NULL,
    `rot_description` VARCHAR(500),
    `rot_created_by` VARCHAR(255),
    `rot_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `rot_updated_by` VARCHAR(255),
    `rot_updated_date` DATETIME(6),
    `rot_version` VARCHAR(50),
    `rot_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `role_type_pkey` PRIMARY KEY (`rot_role_type_id`),
    CONSTRAINT `role_type_rot_role_code_key` UNIQUE (`rot_role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `charge_config` (
    `cc_charge_id` BIGINT NOT NULL AUTO_INCREMENT,
    `cc_product_id` BIGINT NOT NULL,
    `cc_charge_code` VARCHAR(50) NOT NULL,
    `cc_calculation_basis` VARCHAR(30),
    `cc_gl_code` VARCHAR(50),
    `cc_is_active` TINYINT(1) DEFAULT true NOT NULL,
    `cc_created_by` VARCHAR(255),
    `cc_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `cc_updated_by` VARCHAR(255),
    `cc_updated_date` DATETIME(6),
    `cc_version` VARCHAR(50),
    `cc_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `charge_config_pkey` PRIMARY KEY (`cc_charge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `charge_slab` (
    `cs_slab_id` BIGINT NOT NULL AUTO_INCREMENT,
    `cs_charge_id` BIGINT NOT NULL,
    `cs_slab_attribute` VARCHAR(100),
    `cs_value_from` DECIMAL(15,4),
    `cs_value_to` DECIMAL(15,4),
    `cs_charge_amount` DECIMAL(22,8),
    `cs_currency_id` BIGINT NOT NULL,
    `cs_created_by` VARCHAR(255),
    `cs_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `cs_updated_by` VARCHAR(255),
    `cs_updated_date` DATETIME(6),
    `cs_version` VARCHAR(50),
    `cs_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    `cs_charge_unit` VARCHAR(30) DEFAULT 'FIXED_AMOUNT' NOT NULL,
    `cs_minimum_charge` DECIMAL(22,8),
    `cs_maximum_charge` DECIMAL(22,8),
    CONSTRAINT `charge_slab_pkey` PRIMARY KEY (`cs_slab_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `currency` (
    `cur_currency_id` BIGINT NOT NULL AUTO_INCREMENT,
    `cur_iso_code` VARCHAR(10) NOT NULL,
    `cur_iso_numeric_code` VARCHAR(10),
    `cur_currency_name` VARCHAR(255) NOT NULL,
    `cur_currency_symbol` VARCHAR(10),
    `cur_decimal_places` INT,
    `cur_is_active` TINYINT(1) DEFAULT true NOT NULL,
    `cur_created_by` VARCHAR(255),
    `cur_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `cur_updated_by` VARCHAR(255),
    `cur_updated_date` DATETIME(6),
    `cur_version` VARCHAR(50),
    `cur_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `currency_cur_iso_code_key` UNIQUE (`cur_iso_code`),
    CONSTRAINT `currency_pkey` PRIMARY KEY (`cur_currency_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `sweep_config` (
    `sc_sweep_id` BIGINT NOT NULL AUTO_INCREMENT,
    `sc_source_product_id` BIGINT NOT NULL,
    `sc_target_product_id` BIGINT NOT NULL,
    `sc_sweep_type` VARCHAR(30),
    `sc_threshold_amount` DECIMAL(18,4),
    `sc_sweep_multiple` DECIMAL(18,4),
    `sc_currency_id` BIGINT NOT NULL,
    `sc_created_by` VARCHAR(255),
    `sc_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `sc_updated_by` VARCHAR(255),
    `sc_updated_date` DATETIME(6),
    `sc_version` VARCHAR(20),
    `sc_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    `sc_operator_user_id` BIGINT,
    CONSTRAINT `sweep_config_pkey` PRIMARY KEY (`sc_sweep_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `gl_code_mapping` (
    `gcm_mapping_id` BIGINT NOT NULL AUTO_INCREMENT,
    `gcm_outcome_id` BIGINT NOT NULL,
    `gcm_gl_code` VARCHAR(50) NOT NULL,
    `gcm_created_by` VARCHAR(255),
    `gcm_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `gcm_updated_by` VARCHAR(255),
    `gcm_updated_date` DATETIME(6),
    `gcm_version` VARCHAR(20),
    `gcm_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    `gcm_operator_user_id` BIGINT,
    CONSTRAINT `gl_code_mapping_pkey` PRIMARY KEY (`gcm_mapping_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE INDEX `idx_ad_category` ON `attribute_definitions` (`ad_category_id`);
CREATE INDEX `idx_cc_product` ON `charge_config` (`cc_product_id`);
CREATE INDEX `idx_cs_charge` ON `charge_slab` (`cs_charge_id`);
CREATE INDEX `idx_cs_currency` ON `charge_slab` (`cs_currency_id`);
CREATE INDEX `idx_gcm_outcome` ON `gl_code_mapping` (`gcm_outcome_id`);
CREATE INDEX `idx_pav_attribute_def` ON `product_attribute_values` (`pav_attribute_def_id`);
CREATE INDEX `idx_pav_product` ON `product_attribute_values` (`pav_product_id`);
CREATE INDEX `idx_pc_parent_category` ON `product_category` (`pc_parent_category_id`);
CREATE INDEX `idx_pcc_currency` ON `product_currency_config` (`pcc_currency_id`);
CREATE INDEX `idx_pcc_product` ON `product_currency_config` (`pcc_product_id`);
CREATE INDEX `idx_pr_product` ON `product_rule` (`pr_product_id`);
CREATE INDEX `idx_pr_rule_type` ON `product_rule` (`pr_rule_type_id`);
CREATE INDEX `idx_prc_product` ON `product_role_config` (`prc_product_id`);
CREATE INDEX `idx_prc_role_type` ON `product_role_config` (`prc_role_type_id`);
CREATE INDEX `idx_pro_base_currency` ON `product` (`pro_base_currency_code`);
CREATE INDEX `idx_pro_category` ON `product` (`pro_category_id`);
CREATE INDEX `idx_rc_rule` ON `rule_condition` (`rc_rule_id`);
CREATE INDEX `idx_ro_currency` ON `rule_outcome` (`ro_currency_id`);
CREATE INDEX `idx_ro_rule` ON `rule_outcome` (`ro_rule_id`);
CREATE INDEX `idx_sc_currency` ON `sweep_config` (`sc_currency_id`);
CREATE INDEX `idx_sc_source_product` ON `sweep_config` (`sc_source_product_id`);
CREATE INDEX `idx_sc_target_product` ON `sweep_config` (`sc_target_product_id`);
CREATE UNIQUE INDEX `uq_attribute_definition_category_key` ON `attribute_definitions` (`ad_category_id`, `ad_attribute_key`);
CREATE UNIQUE INDEX `uq_product_currency_config_one_base` ON `product_currency_config` (pcc_base_product_id);
CREATE UNIQUE INDEX `uq_product_currency_config_product_currency` ON `product_currency_config` (`pcc_product_id`, `pcc_currency_id`);

-- Local FKs are added after every table and index exists.
ALTER TABLE `attribute_definitions` ADD CONSTRAINT `attribute_definitions_ad_category_id_fkey` FOREIGN KEY (`ad_category_id`) REFERENCES `product_category`(`pc_category_id`);
ALTER TABLE `charge_config` ADD CONSTRAINT `charge_config_cc_product_id_fkey` FOREIGN KEY (`cc_product_id`) REFERENCES `product`(`pro_product_id`);
ALTER TABLE `charge_slab` ADD CONSTRAINT `charge_slab_cs_charge_id_fkey` FOREIGN KEY (`cs_charge_id`) REFERENCES `charge_config`(`cc_charge_id`);
ALTER TABLE `charge_slab` ADD CONSTRAINT `charge_slab_cs_currency_id_fkey` FOREIGN KEY (`cs_currency_id`) REFERENCES `currency`(`cur_currency_id`);
ALTER TABLE `gl_code_mapping` ADD CONSTRAINT `gl_code_mapping_gcm_outcome_id_fkey` FOREIGN KEY (`gcm_outcome_id`) REFERENCES `rule_outcome`(`ro_outcome_id`);
ALTER TABLE `product_attribute_values` ADD CONSTRAINT `product_attribute_values_pav_attribute_def_id_fkey` FOREIGN KEY (`pav_attribute_def_id`) REFERENCES `attribute_definitions`(`ad_attribute_def_id`);
ALTER TABLE `product_attribute_values` ADD CONSTRAINT `product_attribute_values_pav_product_id_fkey` FOREIGN KEY (`pav_product_id`) REFERENCES `product`(`pro_product_id`);
ALTER TABLE `product_category` ADD CONSTRAINT `product_category_pc_parent_category_id_fkey` FOREIGN KEY (`pc_parent_category_id`) REFERENCES `product_category`(`pc_category_id`);
ALTER TABLE `product_configuration_revision` ADD CONSTRAINT `product_configuration_revision_pcr_product_id_fkey` FOREIGN KEY (`pcr_product_id`) REFERENCES `product`(`pro_product_id`);
ALTER TABLE `product_currency_config` ADD CONSTRAINT `product_currency_config_pcc_currency_id_fkey` FOREIGN KEY (`pcc_currency_id`) REFERENCES `currency`(`cur_currency_id`);
ALTER TABLE `product_currency_config` ADD CONSTRAINT `product_currency_config_pcc_product_id_fkey` FOREIGN KEY (`pcc_product_id`) REFERENCES `product`(`pro_product_id`);
ALTER TABLE `product` ADD CONSTRAINT `product_pro_base_currency_code_fkey` FOREIGN KEY (`pro_base_currency_code`) REFERENCES `currency`(`cur_iso_code`);
ALTER TABLE `product` ADD CONSTRAINT `product_pro_category_id_fkey` FOREIGN KEY (`pro_category_id`) REFERENCES `product_category`(`pc_category_id`);
ALTER TABLE `product_role_config` ADD CONSTRAINT `product_role_config_prc_product_id_fkey` FOREIGN KEY (`prc_product_id`) REFERENCES `product`(`pro_product_id`);
ALTER TABLE `product_role_config` ADD CONSTRAINT `product_role_config_prc_role_type_id_fkey` FOREIGN KEY (`prc_role_type_id`) REFERENCES `role_type`(`rot_role_type_id`);
ALTER TABLE `product_rule` ADD CONSTRAINT `product_rule_pr_product_id_fkey` FOREIGN KEY (`pr_product_id`) REFERENCES `product`(`pro_product_id`);
ALTER TABLE `product_rule` ADD CONSTRAINT `product_rule_pr_rule_type_id_fkey` FOREIGN KEY (`pr_rule_type_id`) REFERENCES `rule_type`(`rut_rule_type_id`);
ALTER TABLE `rule_condition` ADD CONSTRAINT `rule_condition_rc_rule_id_fkey` FOREIGN KEY (`rc_rule_id`) REFERENCES `product_rule`(`pr_rule_id`);
ALTER TABLE `rule_outcome` ADD CONSTRAINT `rule_outcome_ro_currency_id_fkey` FOREIGN KEY (`ro_currency_id`) REFERENCES `currency`(`cur_currency_id`);
ALTER TABLE `rule_outcome` ADD CONSTRAINT `rule_outcome_ro_rule_id_fkey` FOREIGN KEY (`ro_rule_id`) REFERENCES `product_rule`(`pr_rule_id`);
ALTER TABLE `sweep_config` ADD CONSTRAINT `sweep_config_sc_currency_id_fkey` FOREIGN KEY (`sc_currency_id`) REFERENCES `currency`(`cur_currency_id`);
ALTER TABLE `sweep_config` ADD CONSTRAINT `sweep_config_sc_source_product_id_fkey` FOREIGN KEY (`sc_source_product_id`) REFERENCES `product`(`pro_product_id`);
ALTER TABLE `sweep_config` ADD CONSTRAINT `sweep_config_sc_target_product_id_fkey` FOREIGN KEY (`sc_target_product_id`) REFERENCES `product`(`pro_product_id`);

-- Insert business change and event in ONE transaction. Poll pending rows;
-- mark published only after Kafka ACK. Retry with the same event_uuid.
-- Single publisher initially. Consumers apply only newer source_version snapshots.
-- Topics: user.snapshot (key=user UUID); product.catalog (key=product ID string).
CREATE TABLE outbox_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT (UUID()),
    event_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    source_version BIGINT NOT NULL,
    payload JSON NOT NULL,
    is_published TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at DATETIME(6),
    UNIQUE KEY uq_outbox_event_uuid (event_uuid),
    KEY idx_outbox_pending (is_published, id)
) ENGINE=InnoDB;

