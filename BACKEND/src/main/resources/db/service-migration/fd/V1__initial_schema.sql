-- MoneyPal college project: fd; MySQL 8.4 / MySQL 8.0.16+.
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
USE moneypal_fd;

CREATE TABLE `fd_accounts` (
    `fa_account_id` BIGINT NOT NULL AUTO_INCREMENT,
    `fa_account_number` VARCHAR(50) NOT NULL,
    `fa_primary_holder_id` BIGINT NOT NULL,
    `fa_product_id` BIGINT NOT NULL,
    `fa_applied_rate_outcome_id` BIGINT,
    `fa_principal_amount` DECIMAL(18,4) NOT NULL,
    `fa_currency_id` BIGINT NOT NULL,
    `fa_tenure_months` INT NOT NULL,
    `fa_interest_rate` DECIMAL(9,4),
    `fa_compounding_frequency` VARCHAR(30),
    `fa_payout_frequency` VARCHAR(30),
    `fa_auto_renewal` TINYINT(1) DEFAULT false NOT NULL,
    `fa_open_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `fa_maturity_date` DATETIME(6),
    `fa_maturity_amount` DECIMAL(18,4),
    `fa_status` VARCHAR(30) DEFAULT 'PENDING' NOT NULL,
    `fa_created_by` VARCHAR(255),
    `fa_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `fa_updated_by` VARCHAR(255),
    `fa_updated_date` DATETIME(6),
    `fa_version` VARCHAR(50),
    `fa_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    `fa_row_version` BIGINT DEFAULT 0 NOT NULL,
    `fa_policy_version` VARCHAR(50) DEFAULT 'academic-v1' NOT NULL,
    `fa_funding_savings_account_id` BIGINT,
    CONSTRAINT `ck_fd_accounts_policy_version_nonblank` CHECK ((CHAR_LENGTH(TRIM((`fa_policy_version`))) > 0)),
    CONSTRAINT `ck_fd_accounts_principal_positive` CHECK ((`fa_principal_amount` > (0))),
    CONSTRAINT `ck_fd_accounts_status` CHECK (((`fa_status`) IN ('PENDING', 'ACTIVE', 'MATURED', 'CLOSED', 'PREMATURELY_CLOSED'))),
    CONSTRAINT `ck_fd_accounts_tenure_positive` CHECK ((`fa_tenure_months` > 0)),
    fa_terms_snapshot JSON,
    CONSTRAINT `fd_accounts_fa_account_number_key` UNIQUE (`fa_account_number`),
    CONSTRAINT `fd_accounts_pkey` PRIMARY KEY (`fa_account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `fd_interest_accrual` (
    `fia_accrual_id` BIGINT NOT NULL AUTO_INCREMENT,
    `fia_account_id` BIGINT NOT NULL,
    `fia_accrual_date` DATETIME(6) NOT NULL,
    `fia_accrued_amount` DECIMAL(22,8) NOT NULL,
    `fia_cumulative_accrued` DECIMAL(22,8),
    `fia_currency_id` BIGINT NOT NULL,
    `fia_created_by` VARCHAR(255),
    `fia_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `fia_updated_by` VARCHAR(255),
    `fia_updated_date` DATETIME(6),
    `fia_version` VARCHAR(50),
    `fia_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    `fia_compounding_balance` DECIMAL(22,8),
    `fia_capitalized_amount` DECIMAL(22,8) DEFAULT 0 NOT NULL,
    CONSTRAINT `ck_fd_interest_accrual_capitalized_nonnegative` CHECK ((`fia_capitalized_amount` >= (0))),
    CONSTRAINT `fd_interest_accrual_pkey` PRIMARY KEY (`fia_accrual_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `fd_interest_payout` (
    `fip_payout_id` BIGINT NOT NULL AUTO_INCREMENT,
    `fip_account_id` BIGINT NOT NULL,
    `fip_payout_date` DATETIME(6) NOT NULL,
    `fip_amount` DECIMAL(18,4) NOT NULL,
    `fip_payout_status` VARCHAR(30) DEFAULT 'PENDING' NOT NULL,
    `fip_currency_id` BIGINT NOT NULL,
    `fip_created_by` VARCHAR(255),
    `fip_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `fip_updated_by` VARCHAR(255),
    `fip_updated_date` DATETIME(6),
    `fip_version` VARCHAR(50),
    `fip_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `fd_interest_payout_pkey` PRIMARY KEY (`fip_payout_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `fd_premature_closure` (
    `fpc_closure_id` BIGINT NOT NULL AUTO_INCREMENT,
    `fpc_account_id` BIGINT NOT NULL,
    `fpc_closure_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `fpc_penalty_pct_applied` DECIMAL(9,4),
    `fpc_penalty_amount` DECIMAL(18,4),
    `fpc_net_payable_amount` DECIMAL(18,4),
    `fpc_currency_id` BIGINT NOT NULL,
    `fpc_reason` VARCHAR(500),
    `fpc_applied_rule_outcome_id` BIGINT,
    `fpc_approved_by` BIGINT,
    `fpc_created_by` VARCHAR(255),
    `fpc_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `fpc_updated_by` VARCHAR(255),
    `fpc_updated_date` DATETIME(6),
    `fpc_version` VARCHAR(50),
    `fpc_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    `fpc_status` VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    CONSTRAINT `ck_fd_premature_closure_status` CHECK (((`fpc_status`) IN ('PENDING', 'APPROVED'))),
    CONSTRAINT `fd_premature_closure_pkey` PRIMARY KEY (`fpc_closure_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `fd_maturity_instructions` (
    `fmi_instruction_id` BIGINT NOT NULL AUTO_INCREMENT,
    `fmi_account_id` BIGINT NOT NULL,
    `fmi_instruction_type` VARCHAR(50) NOT NULL,
    `fmi_renewal_tenure_months` INT,
    `fmi_target_payout_account_id` BIGINT,
    `fmi_created_by` VARCHAR(255),
    `fmi_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `fmi_updated_by` VARCHAR(255),
    `fmi_updated_date` DATETIME(6),
    `fmi_version` VARCHAR(50),
    `fmi_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `fd_maturity_instructions_pkey` PRIMARY KEY (`fmi_instruction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `fd_payout_accounts` (
    `fpa_payout_account_id` BIGINT NOT NULL AUTO_INCREMENT,
    `fpa_account_id` BIGINT NOT NULL,
    `fpa_bank_account_number` VARCHAR(50) NOT NULL,
    `fpa_ifsc_code` VARCHAR(20),
    `fpa_account_holder_name` VARCHAR(255),
    `fpa_is_primary` TINYINT(1) DEFAULT false NOT NULL,
    `fpa_created_by` VARCHAR(255),
    `fpa_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `fpa_updated_by` VARCHAR(255),
    `fpa_updated_date` DATETIME(6),
    `fpa_version` VARCHAR(50),
    `fpa_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `fd_payout_accounts_pkey` PRIMARY KEY (`fpa_payout_account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `fd_notices` (
    `fn_notice_id` BIGINT NOT NULL AUTO_INCREMENT,
    `fn_account_id` BIGINT NOT NULL,
    `fn_notice_type` VARCHAR(50),
    `fn_notice_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `fn_channel` VARCHAR(30),
    `fn_delivery_status` VARCHAR(30),
    `fn_created_by` VARCHAR(255),
    `fn_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `fn_updated_by` VARCHAR(255),
    `fn_updated_date` DATETIME(6),
    `fn_version` VARCHAR(50),
    `fn_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `fd_notices_pkey` PRIMARY KEY (`fn_notice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `fd_statements` (
    `fs_statement_id` BIGINT NOT NULL AUTO_INCREMENT,
    `fs_account_id` BIGINT NOT NULL,
    `fs_period_start` DATETIME(6),
    `fs_period_end` DATETIME(6),
    `fs_generated_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `fs_file_reference` VARCHAR(255),
    `fs_created_by` VARCHAR(255),
    `fs_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `fs_updated_by` VARCHAR(255),
    `fs_updated_date` DATETIME(6),
    `fs_version` VARCHAR(50),
    `fs_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `fd_statements_pkey` PRIMARY KEY (`fs_statement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `fd_transactions` (
    `ft_transaction_id` BIGINT NOT NULL AUTO_INCREMENT,
    `ft_account_id` BIGINT NOT NULL,
    `ft_txn_type_id` BIGINT NOT NULL,
    `ft_amount` DECIMAL(18,4) NOT NULL,
    `ft_txn_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `ft_balance_after` DECIMAL(18,4),
    `ft_currency_id` BIGINT NOT NULL,
    `ft_remarks` VARCHAR(500),
    `ft_created_by` VARCHAR(255),
    `ft_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `ft_updated_by` VARCHAR(255),
    `ft_updated_date` DATETIME(6),
    `ft_version` VARCHAR(50),
    `ft_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `fd_transactions_pkey` PRIMARY KEY (`ft_transaction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `fd_journals` (
    `fj_id` BIGINT NOT NULL AUTO_INCREMENT,
    `fj_key` VARCHAR(100) NOT NULL,
    `fj_fd_account_id` BIGINT,
    `fj_savings_account_id` BIGINT NOT NULL,
    `fj_currency_id` BIGINT NOT NULL,
    `fj_operation` VARCHAR(30) NOT NULL,
    `fj_posted_at` DATETIME(6) NOT NULL,
    `fj_total_debit` DECIMAL(18,4) NOT NULL,
    `fj_total_credit` DECIMAL(18,4) NOT NULL,
    `fj_savings_balance_after` DECIMAL(18,4),
    CONSTRAINT `ck_fd_journal_balanced` CHECK ((`fj_total_debit` = `fj_total_credit`)),
    CONSTRAINT `fd_journals_fj_operation_check` CHECK (((`fj_operation`) IN ('FUNDING', 'INTEREST', 'SETTLEMENT', 'DEMO_OPENING'))),
    CONSTRAINT `fd_journals_fj_total_debit_check` CHECK ((`fj_total_debit` > (0))),
    CONSTRAINT `fd_journals_fj_key_key` UNIQUE (`fj_key`),
    CONSTRAINT `fd_journals_pkey` PRIMARY KEY (`fj_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `fd_journal_lines` (
    `jl_journal_id` BIGINT NOT NULL,
    `jl_sequence` INT NOT NULL,
    `jl_account_code` VARCHAR(50) NOT NULL,
    `jl_debit` DECIMAL(18,4) NOT NULL,
    `jl_credit` DECIMAL(18,4) NOT NULL,
    CONSTRAINT `ck_fd_journal_line_side` CHECK ((((`jl_debit` > (0)) AND (`jl_credit` = (0))) OR ((`jl_credit` > (0)) AND (`jl_debit` = (0))))),
    CONSTRAINT `fd_journal_lines_pkey` PRIMARY KEY (`jl_journal_id`, `jl_sequence`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `fd_account_events` (
    `fae_event_id` BIGINT NOT NULL AUTO_INCREMENT,
    `fae_account_id` BIGINT NOT NULL,
    `fae_event_type` VARCHAR(50) NOT NULL,
    `fae_payload` JSON,
    `fae_is_published` TINYINT(1) DEFAULT false NOT NULL,
    `fae_created_by` VARCHAR(255),
    `fae_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `fae_updated_by` VARCHAR(255),
    `fae_updated_date` DATETIME(6),
    `fae_version` VARCHAR(50),
    `fae_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    fae_aggregate_version BIGINT NOT NULL DEFAULT 0,
    fae_published_at DATETIME(6),
    CONSTRAINT `fd_account_events_pkey` PRIMARY KEY (`fae_event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `fd_operation_idempotency` (
    `foi_id` BIGINT NOT NULL AUTO_INCREMENT,
    `foi_operation_scope` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `foi_actor_key` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `foi_idempotency_key` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `foi_request_hash` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `foi_aggregate_id` BIGINT,
    `foi_status` VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    `foi_response_reference` VARCHAR(255),
    `foi_created_at` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `foi_completed_at` DATETIME(6),
    `foi_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `ck_fd_operation_idempotency_actor_nonblank` CHECK ((CHAR_LENGTH(TRIM((`foi_actor_key`))) > 0)),
    CONSTRAINT `ck_fd_operation_idempotency_key_nonblank` CHECK ((CHAR_LENGTH(TRIM((`foi_idempotency_key`))) > 0)),
    CONSTRAINT `ck_fd_operation_idempotency_scope_nonblank` CHECK ((CHAR_LENGTH(TRIM((`foi_operation_scope`))) > 0)),
    CONSTRAINT `ck_fd_operation_idempotency_status` CHECK (((`foi_status`) IN ('PENDING', 'COMPLETED', 'FAILED'))),
    CONSTRAINT `fd_operation_idempotency_pkey` PRIMARY KEY (`foi_id`),
    CONSTRAINT `uq_fd_operation_idempotency_scope_key` UNIQUE (`foi_operation_scope`, `foi_actor_key`, `foi_idempotency_key`),
    CONSTRAINT `uq_fd_operation_idempotency_uuid` UNIQUE (`foi_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `account_holder_roles` (
    `ahr_id` BIGINT NOT NULL AUTO_INCREMENT,
    `ahr_account_id` BIGINT NOT NULL,
    `ahr_user_id` BIGINT NOT NULL,
    `ahr_role_type_id` BIGINT NOT NULL,
    `ahr_holder_sequence` INT,
    `ahr_operating_mandate` VARCHAR(50),
    `ahr_tds_applicable` TINYINT(1) DEFAULT true NOT NULL,
    `ahr_created_by` VARCHAR(255),
    `ahr_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `ahr_updated_by` VARCHAR(255),
    `ahr_updated_date` DATETIME(6),
    `ahr_version` VARCHAR(50),
    `ahr_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `account_holder_roles_pkey` PRIMARY KEY (`ahr_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `transaction_types` (
    `tt_txn_type_id` BIGINT NOT NULL AUTO_INCREMENT,
    `tt_txn_type_code` VARCHAR(50) NOT NULL,
    `tt_txn_type_name` VARCHAR(255) NOT NULL,
    `tt_debit_credit` VARCHAR(10),
    `tt_requires_approval` TINYINT(1) DEFAULT false NOT NULL,
    `tt_created_by` VARCHAR(255),
    `tt_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `tt_updated_by` VARCHAR(255),
    `tt_updated_date` DATETIME(6),
    `tt_version` VARCHAR(50),
    `tt_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `transaction_types_pkey` PRIMARY KEY (`tt_txn_type_id`),
    CONSTRAINT `transaction_types_tt_txn_type_code_key` UNIQUE (`tt_txn_type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `savings_accounts` (
    `sa_account_id` BIGINT NOT NULL AUTO_INCREMENT,
    `sa_account_number` VARCHAR(50) NOT NULL,
    `sa_holder_id` BIGINT NOT NULL,
    `sa_currency_id` BIGINT NOT NULL,
    `sa_display_name` VARCHAR(80) NOT NULL,
    `sa_balance` DECIMAL(18,4) DEFAULT 0 NOT NULL,
    `sa_status` VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    `sa_created_date` DATETIME(6) NOT NULL,
    `sa_row_version` BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT `savings_accounts_sa_balance_check` CHECK ((`sa_balance` >= (0))),
    CONSTRAINT `savings_accounts_sa_status_check` CHECK (((`sa_status`) IN ('ACTIVE', 'INACTIVE'))),
    CONSTRAINT `savings_accounts_pkey` PRIMARY KEY (`sa_account_id`),
    CONSTRAINT `savings_accounts_sa_account_number_key` UNIQUE (`sa_account_number`),
    CONSTRAINT `uq_savings_holder_currency` UNIQUE (`sa_account_id`, `sa_holder_id`, `sa_currency_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `savings_transactions` (
    `st_id` BIGINT NOT NULL AUTO_INCREMENT,
    `st_reference_id` VARCHAR(64) NOT NULL,
    `st_account_id` BIGINT NOT NULL,
    `st_entry_type` VARCHAR(10) NOT NULL,
    `st_amount` DECIMAL(18,4) NOT NULL,
    `st_currency_id` BIGINT NOT NULL,
    `st_balance_after` DECIMAL(18,4) NOT NULL,
    `st_remarks` VARCHAR(500),
    `st_posted_at` DATETIME(6) NOT NULL,
    `st_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `savings_transactions_st_amount_check` CHECK ((`st_amount` > (0))),
    CONSTRAINT `savings_transactions_st_balance_after_check` CHECK ((`st_balance_after` >= (0))),
    CONSTRAINT `savings_transactions_st_entry_type_check` CHECK (((`st_entry_type`) IN ('DEBIT', 'CREDIT'))),
    CONSTRAINT `savings_transactions_pkey` PRIMARY KEY (`st_id`),
    CONSTRAINT `uq_savings_transaction_reference_side` UNIQUE (`st_reference_id`, `st_entry_type`),
    CONSTRAINT `uq_savings_transaction_uuid` UNIQUE (`st_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `fd_product_ref` (
    `fpr_product_id` BIGINT NOT NULL,
    `fpr_product_code` VARCHAR(50) NOT NULL,
    `fpr_product_name` VARCHAR(255) NOT NULL,
    `fpr_status` VARCHAR(30),
    `fpr_created_by` VARCHAR(255),
    `fpr_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `fpr_updated_by` VARCHAR(255),
    `fpr_updated_date` DATETIME(6),
    `fpr_version` VARCHAR(50),
    `fpr_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    fpr_source_version BIGINT NOT NULL DEFAULT 0,
    fpr_synced_at DATETIME(6),
    CONSTRAINT `fd_product_ref_pkey` PRIMARY KEY (`fpr_product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `fd_user_ref` (
    `fur_user_id` BIGINT NOT NULL,
    `fur_user_uuid` VARCHAR(64) NOT NULL,
    `fur_customer_number` VARCHAR(50),
    `fur_full_name` VARCHAR(255),
    `fur_user_status` VARCHAR(30),
    `fur_created_by` VARCHAR(255),
    `fur_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `fur_updated_by` VARCHAR(255),
    `fur_updated_date` DATETIME(6),
    `fur_version` VARCHAR(50),
    `fur_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    fur_source_version BIGINT NOT NULL DEFAULT 0,
    fur_synced_at DATETIME(6),
    CONSTRAINT `fd_user_ref_pkey` PRIMARY KEY (`fur_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `fd_currency_ref` (
    `fcr_currency_id` BIGINT NOT NULL,
    `fcr_iso_code` VARCHAR(10) NOT NULL,
    `fcr_currency_name` VARCHAR(255),
    `fcr_decimal_places` INT,
    `fcr_created_by` VARCHAR(255),
    `fcr_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `fcr_updated_by` VARCHAR(255),
    `fcr_updated_date` DATETIME(6),
    `fcr_version` VARCHAR(50),
    `fcr_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `fd_currency_ref_pkey` PRIMARY KEY (`fcr_currency_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `fd_rule_outcome_ref` (
    `fror_outcome_id` BIGINT NOT NULL AUTO_INCREMENT,
    `fror_outcome_type` VARCHAR(30),
    `fror_outcome_value` VARCHAR(255),
    `fror_unit` VARCHAR(30),
    `fror_created_by` VARCHAR(255),
    `fror_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `fror_updated_by` VARCHAR(255),
    `fror_updated_date` DATETIME(6),
    `fror_version` VARCHAR(50),
    `fror_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    `fror_source_outcome_id` BIGINT,
    CONSTRAINT `fd_rule_outcome_ref_pkey` PRIMARY KEY (`fror_outcome_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `fd_role_type_ref` (
    `frtr_role_type_id` BIGINT NOT NULL,
    `frtr_role_code` VARCHAR(50) NOT NULL,
    `frtr_description` VARCHAR(500),
    `frtr_created_by` VARCHAR(255),
    `frtr_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `frtr_updated_by` VARCHAR(255),
    `frtr_updated_date` DATETIME(6),
    `frtr_version` VARCHAR(50),
    `frtr_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `fd_role_type_ref_pkey` PRIMARY KEY (`frtr_role_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE INDEX `idx_ahr_account` ON `account_holder_roles` (`ahr_account_id`);
CREATE INDEX `idx_ahr_role_type` ON `account_holder_roles` (`ahr_role_type_id`);
CREATE INDEX `idx_ahr_user` ON `account_holder_roles` (`ahr_user_id`);
CREATE INDEX `idx_fa_applied_rate_outcome` ON `fd_accounts` (`fa_applied_rate_outcome_id`);
CREATE INDEX `idx_fa_currency` ON `fd_accounts` (`fa_currency_id`);
CREATE INDEX `idx_fa_primary_holder` ON `fd_accounts` (`fa_primary_holder_id`);
CREATE INDEX `idx_fa_product` ON `fd_accounts` (`fa_product_id`);
CREATE INDEX `idx_fae_account` ON `fd_account_events` (`fae_account_id`);
CREATE INDEX `idx_fae_is_published` ON `fd_account_events` (fae_is_published, fae_event_id);
CREATE INDEX `idx_fd_accounts_status_maturity_date` ON `fd_accounts` (`fa_status`, `fa_maturity_date`);
CREATE INDEX `idx_fd_funding_savings` ON `fd_accounts` (`fa_funding_savings_account_id`);
CREATE INDEX `idx_fd_operation_idempotency_aggregate_id` ON `fd_operation_idempotency` (`foi_aggregate_id`);
CREATE INDEX `idx_fia_account` ON `fd_interest_accrual` (`fia_account_id`);
CREATE INDEX `idx_fia_currency` ON `fd_interest_accrual` (`fia_currency_id`);
CREATE INDEX `idx_fip_account` ON `fd_interest_payout` (`fip_account_id`);
CREATE INDEX `idx_fip_currency` ON `fd_interest_payout` (`fip_currency_id`);
CREATE INDEX `idx_fmi_account` ON `fd_maturity_instructions` (`fmi_account_id`);
CREATE INDEX `idx_fmi_target_payout_account` ON `fd_maturity_instructions` (`fmi_target_payout_account_id`);
CREATE INDEX `idx_fn_account` ON `fd_notices` (`fn_account_id`);
CREATE INDEX `idx_fpa_account` ON `fd_payout_accounts` (`fpa_account_id`);
CREATE INDEX `idx_fpc_account` ON `fd_premature_closure` (`fpc_account_id`);
CREATE INDEX `idx_fpc_applied_rule_outcome` ON `fd_premature_closure` (`fpc_applied_rule_outcome_id`);
CREATE INDEX `idx_fpc_approved_by` ON `fd_premature_closure` (`fpc_approved_by`);
CREATE INDEX `idx_fpc_currency` ON `fd_premature_closure` (`fpc_currency_id`);
CREATE INDEX `idx_fs_account` ON `fd_statements` (`fs_account_id`);
CREATE INDEX `idx_ft_account` ON `fd_transactions` (`ft_account_id`);
CREATE INDEX `idx_ft_currency` ON `fd_transactions` (`ft_currency_id`);
CREATE INDEX `idx_ft_txn_type` ON `fd_transactions` (`ft_txn_type_id`);
CREATE INDEX `idx_savings_holder` ON `savings_accounts` (`sa_holder_id`);
CREATE INDEX `idx_savings_transactions_account_posted` ON `savings_transactions` (`st_account_id`, `st_posted_at`, `st_id`);
CREATE INDEX `idx_savings_transactions_reference` ON `savings_transactions` (`st_reference_id`);
CREATE UNIQUE INDEX `uq_fd_account_events_fae_uuid` ON `fd_account_events` (`fae_uuid`);
CREATE UNIQUE INDEX `uq_fd_accounts_fa_uuid` ON `fd_accounts` (`fa_uuid`);
CREATE UNIQUE INDEX `uq_fd_currency_ref_iso_code` ON `fd_currency_ref` ((UPPER(`fcr_iso_code`)));
CREATE UNIQUE INDEX `uq_fd_interest_accrual_account_date` ON `fd_interest_accrual` (`fia_account_id`, `fia_accrual_date`);
CREATE UNIQUE INDEX `uq_fd_interest_accrual_fia_uuid` ON `fd_interest_accrual` (`fia_uuid`);
CREATE UNIQUE INDEX `uq_fd_interest_payout_fip_uuid` ON `fd_interest_payout` (`fip_uuid`);
CREATE UNIQUE INDEX `uq_fd_premature_closure_fpc_uuid` ON `fd_premature_closure` (`fpc_uuid`);
CREATE UNIQUE INDEX `uq_fd_product_ref_product_code` ON `fd_product_ref` ((UPPER(`fpr_product_code`)));
CREATE UNIQUE INDEX `uq_fd_rule_outcome_ref_source` ON `fd_rule_outcome_ref` (fror_source_outcome_id);
CREATE UNIQUE INDEX `uq_fd_transactions_ft_uuid` ON `fd_transactions` (`ft_uuid`);
CREATE UNIQUE INDEX `uq_fd_user_ref_user_uuid` ON `fd_user_ref` (`fur_user_uuid`);

-- Local FKs are added after every table and index exists.
ALTER TABLE `account_holder_roles` ADD CONSTRAINT `account_holder_roles_ahr_account_id_fkey` FOREIGN KEY (`ahr_account_id`) REFERENCES `fd_accounts`(`fa_account_id`);
ALTER TABLE `account_holder_roles` ADD CONSTRAINT `account_holder_roles_ahr_role_type_id_fkey` FOREIGN KEY (`ahr_role_type_id`) REFERENCES `fd_role_type_ref`(`frtr_role_type_id`);
ALTER TABLE `account_holder_roles` ADD CONSTRAINT `account_holder_roles_ahr_user_id_fkey` FOREIGN KEY (`ahr_user_id`) REFERENCES `fd_user_ref`(`fur_user_id`);
ALTER TABLE `fd_account_events` ADD CONSTRAINT `fd_account_events_fae_account_id_fkey` FOREIGN KEY (`fae_account_id`) REFERENCES `fd_accounts`(`fa_account_id`);
ALTER TABLE `fd_accounts` ADD CONSTRAINT `fd_accounts_fa_applied_rate_outcome_id_fkey` FOREIGN KEY (`fa_applied_rate_outcome_id`) REFERENCES `fd_rule_outcome_ref`(`fror_outcome_id`);
ALTER TABLE `fd_accounts` ADD CONSTRAINT `fd_accounts_fa_currency_id_fkey` FOREIGN KEY (`fa_currency_id`) REFERENCES `fd_currency_ref`(`fcr_currency_id`);
ALTER TABLE `fd_accounts` ADD CONSTRAINT `fd_accounts_fa_primary_holder_id_fkey` FOREIGN KEY (`fa_primary_holder_id`) REFERENCES `fd_user_ref`(`fur_user_id`);
ALTER TABLE `fd_accounts` ADD CONSTRAINT `fd_accounts_fa_product_id_fkey` FOREIGN KEY (`fa_product_id`) REFERENCES `fd_product_ref`(`fpr_product_id`);
ALTER TABLE `fd_interest_accrual` ADD CONSTRAINT `fd_interest_accrual_fia_account_id_fkey` FOREIGN KEY (`fia_account_id`) REFERENCES `fd_accounts`(`fa_account_id`);
ALTER TABLE `fd_interest_accrual` ADD CONSTRAINT `fd_interest_accrual_fia_currency_id_fkey` FOREIGN KEY (`fia_currency_id`) REFERENCES `fd_currency_ref`(`fcr_currency_id`);
ALTER TABLE `fd_interest_payout` ADD CONSTRAINT `fd_interest_payout_fip_account_id_fkey` FOREIGN KEY (`fip_account_id`) REFERENCES `fd_accounts`(`fa_account_id`);
ALTER TABLE `fd_interest_payout` ADD CONSTRAINT `fd_interest_payout_fip_currency_id_fkey` FOREIGN KEY (`fip_currency_id`) REFERENCES `fd_currency_ref`(`fcr_currency_id`);
ALTER TABLE `fd_journal_lines` ADD CONSTRAINT `fd_journal_lines_jl_journal_id_fkey` FOREIGN KEY (`jl_journal_id`) REFERENCES `fd_journals`(`fj_id`);
ALTER TABLE `fd_journals` ADD CONSTRAINT `fd_journals_fj_currency_id_fkey` FOREIGN KEY (`fj_currency_id`) REFERENCES `fd_currency_ref`(`fcr_currency_id`);
ALTER TABLE `fd_journals` ADD CONSTRAINT `fd_journals_fj_fd_account_id_fkey` FOREIGN KEY (`fj_fd_account_id`) REFERENCES `fd_accounts`(`fa_account_id`);
ALTER TABLE `fd_journals` ADD CONSTRAINT `fd_journals_fj_savings_account_id_fkey` FOREIGN KEY (`fj_savings_account_id`) REFERENCES `savings_accounts`(`sa_account_id`);
ALTER TABLE `fd_maturity_instructions` ADD CONSTRAINT `fd_maturity_instructions_fmi_account_id_fkey` FOREIGN KEY (`fmi_account_id`) REFERENCES `fd_accounts`(`fa_account_id`);
ALTER TABLE `fd_maturity_instructions` ADD CONSTRAINT `fd_maturity_instructions_fmi_target_payout_account_id_fkey` FOREIGN KEY (`fmi_target_payout_account_id`) REFERENCES `fd_payout_accounts`(`fpa_payout_account_id`);
ALTER TABLE `fd_notices` ADD CONSTRAINT `fd_notices_fn_account_id_fkey` FOREIGN KEY (`fn_account_id`) REFERENCES `fd_accounts`(`fa_account_id`);
ALTER TABLE `fd_payout_accounts` ADD CONSTRAINT `fd_payout_accounts_fpa_account_id_fkey` FOREIGN KEY (`fpa_account_id`) REFERENCES `fd_accounts`(`fa_account_id`);
ALTER TABLE `fd_premature_closure` ADD CONSTRAINT `fd_premature_closure_fpc_account_id_fkey` FOREIGN KEY (`fpc_account_id`) REFERENCES `fd_accounts`(`fa_account_id`);
ALTER TABLE `fd_premature_closure` ADD CONSTRAINT `fd_premature_closure_fpc_applied_rule_outcome_id_fkey` FOREIGN KEY (`fpc_applied_rule_outcome_id`) REFERENCES `fd_rule_outcome_ref`(`fror_outcome_id`);
ALTER TABLE `fd_premature_closure` ADD CONSTRAINT `fd_premature_closure_fpc_approved_by_fkey` FOREIGN KEY (`fpc_approved_by`) REFERENCES `fd_user_ref`(`fur_user_id`);
ALTER TABLE `fd_premature_closure` ADD CONSTRAINT `fd_premature_closure_fpc_currency_id_fkey` FOREIGN KEY (`fpc_currency_id`) REFERENCES `fd_currency_ref`(`fcr_currency_id`);
-- Removed remote FK fd_rule_outcome_ref_fror_source_outcome_id_fkey; source ID stays as data.
ALTER TABLE `fd_statements` ADD CONSTRAINT `fd_statements_fs_account_id_fkey` FOREIGN KEY (`fs_account_id`) REFERENCES `fd_accounts`(`fa_account_id`);
ALTER TABLE `fd_transactions` ADD CONSTRAINT `fd_transactions_ft_account_id_fkey` FOREIGN KEY (`ft_account_id`) REFERENCES `fd_accounts`(`fa_account_id`);
ALTER TABLE `fd_transactions` ADD CONSTRAINT `fd_transactions_ft_currency_id_fkey` FOREIGN KEY (`ft_currency_id`) REFERENCES `fd_currency_ref`(`fcr_currency_id`);
ALTER TABLE `fd_transactions` ADD CONSTRAINT `fd_transactions_ft_txn_type_id_fkey` FOREIGN KEY (`ft_txn_type_id`) REFERENCES `transaction_types`(`tt_txn_type_id`);
ALTER TABLE `fd_accounts` ADD CONSTRAINT `fk_fd_funding_savings` FOREIGN KEY (`fa_funding_savings_account_id`, `fa_primary_holder_id`, `fa_currency_id`) REFERENCES `savings_accounts`(`sa_account_id`, `sa_holder_id`, `sa_currency_id`);
ALTER TABLE `savings_accounts` ADD CONSTRAINT `savings_accounts_sa_currency_id_fkey` FOREIGN KEY (`sa_currency_id`) REFERENCES `fd_currency_ref`(`fcr_currency_id`);
ALTER TABLE `savings_accounts` ADD CONSTRAINT `savings_accounts_sa_holder_id_fkey` FOREIGN KEY (`sa_holder_id`) REFERENCES `fd_user_ref`(`fur_user_id`);
ALTER TABLE `savings_transactions` ADD CONSTRAINT `savings_transactions_st_account_id_fkey` FOREIGN KEY (`st_account_id`) REFERENCES `savings_accounts`(`sa_account_id`);
ALTER TABLE `savings_transactions` ADD CONSTRAINT `savings_transactions_st_currency_id_fkey` FOREIGN KEY (`st_currency_id`) REFERENCES `fd_currency_ref`(`fcr_currency_id`);

-- User/product/currency/role replica IDs are assigned from owner services.
-- fror_outcome_id remains a local generated key; fror_source_outcome_id is the Product ID.
-- Populate user/product replicas by Kafka or REST before opening an FD.
-- Currency/holder-role/rule-outcome replicas also need matching seed or API data.
-- No seed values are invented here. These files create EMPTY tables.
-- For simplicity existing savings-to-local-reference FKs are retained.
-- Freeze opening rate/tenure/terms in fd_accounts and fa_terms_snapshot.
-- UNIQUE(fj_fd_account_id,fj_operation) removed; fj_key remains UNIQUE.
-- Use FUNDING:<fdId>, INTEREST:<fdId>:<period>, SETTLEMENT:<fdId>.
-- Maturity and premature closure use the SAME settlement key.
-- Header balance CHECK retained; application must also check journal line totals.
-- Savings update, journal, FD state change and outbox write share one transaction.

