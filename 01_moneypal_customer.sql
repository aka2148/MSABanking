-- MoneyPal college project: customer; MySQL 8.4 / MySQL 8.0.16+.
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
CREATE DATABASE IF NOT EXISTS moneypal_customer CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
USE moneypal_customer;
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE `users` (
    `usr_user_id` BIGINT NOT NULL AUTO_INCREMENT,
    `usr_user_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    `usr_user_type` VARCHAR(30) NOT NULL,
    `usr_status` VARCHAR(30) DEFAULT 'ACTIVE' NOT NULL,
    `usr_created_by` VARCHAR(255),
    `usr_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `usr_updated_by` VARCHAR(255),
    `usr_updated_date` DATETIME(6),
    `usr_version` VARCHAR(50),
    `usr_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    usr_event_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT `users_pkey` PRIMARY KEY (`usr_user_id`),
    CONSTRAINT `users_usr_user_uuid_key` UNIQUE (`usr_user_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `user_credentials` (
    `ucr_credential_id` BIGINT NOT NULL AUTO_INCREMENT,
    `ucr_user_id` BIGINT NOT NULL,
    `ucr_credential_type` VARCHAR(30) DEFAULT 'PASSWORD' NOT NULL,
    `ucr_username` VARCHAR(255) NOT NULL,
    `ucr_password_hash` VARCHAR(255) NOT NULL,
    `ucr_must_change_password` TINYINT(1) DEFAULT false NOT NULL,
    `ucr_failed_attempt_count` INT DEFAULT 0 NOT NULL,
    `ucr_locked_until` DATETIME(6),
    `ucr_mfa_enabled` TINYINT(1) DEFAULT false NOT NULL,
    `ucr_password_changed_at` DATETIME(6),
    `ucr_last_login_at` DATETIME(6),
    `ucr_last_login_ip` VARCHAR(64),
    `ucr_created_by` VARCHAR(255),
    `ucr_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `ucr_updated_by` VARCHAR(255),
    `ucr_updated_date` DATETIME(6),
    `ucr_version` VARCHAR(50),
    `ucr_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `user_credentials_pkey` PRIMARY KEY (`ucr_credential_id`),
    CONSTRAINT `user_credentials_ucr_username_key` UNIQUE (`ucr_username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `user_password_history` (
    `uph_history_id` BIGINT NOT NULL AUTO_INCREMENT,
    `uph_user_id` BIGINT NOT NULL,
    `uph_password_hash` VARCHAR(255) NOT NULL,
    `uph_changed_at` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `uph_created_by` VARCHAR(255),
    `uph_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `uph_updated_by` VARCHAR(255),
    `uph_updated_date` DATETIME(6),
    `uph_version` VARCHAR(50),
    `uph_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `user_password_history_pkey` PRIMARY KEY (`uph_history_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `user_roles` (
    `ur_user_role_id` BIGINT NOT NULL AUTO_INCREMENT,
    `ur_user_id` BIGINT NOT NULL,
    `ur_role_id` BIGINT NOT NULL,
    `ur_assigned_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `ur_created_by` VARCHAR(255),
    `ur_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `ur_updated_by` VARCHAR(255),
    `ur_updated_date` DATETIME(6),
    `ur_version` VARCHAR(50),
    `ur_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `user_roles_pkey` PRIMARY KEY (`ur_user_role_id`),
    CONSTRAINT `user_roles_ur_user_id_ur_role_id_key` UNIQUE (`ur_user_id`, `ur_role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `roles` (
    `rol_role_id` BIGINT NOT NULL AUTO_INCREMENT,
    `rol_role_code` VARCHAR(50) NOT NULL,
    `rol_role_name` VARCHAR(255) NOT NULL,
    `rol_description` VARCHAR(500),
    `rol_created_by` VARCHAR(255),
    `rol_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `rol_updated_by` VARCHAR(255),
    `rol_updated_date` DATETIME(6),
    `rol_version` VARCHAR(50),
    `rol_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `roles_pkey` PRIMARY KEY (`rol_role_id`),
    CONSTRAINT `roles_rol_role_code_key` UNIQUE (`rol_role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `refresh_tokens` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `token` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `user_id` BIGINT NOT NULL,
    `expiry_date` DATETIME(6) NOT NULL,
    `revoked` TINYINT(1) DEFAULT false NOT NULL,
    `created_at` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    CONSTRAINT `refresh_tokens_pkey` PRIMARY KEY (`id`),
    CONSTRAINT `refresh_tokens_token_key` UNIQUE (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `customers` (
    `cus_customer_id` BIGINT NOT NULL AUTO_INCREMENT,
    `cus_user_id` BIGINT NOT NULL,
    `cus_customer_number` VARCHAR(50) NOT NULL,
    `cus_legal_entity_type` VARCHAR(30),
    `cus_home_branch_id` BIGINT,
    `cus_kyc_status` VARCHAR(30) DEFAULT 'PENDING' NOT NULL,
    `cus_risk_category` VARCHAR(30),
    `cus_signup_contact_method` VARCHAR(30),
    `cus_classification_type_id` BIGINT,
    `cus_created_by` VARCHAR(255),
    `cus_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `cus_updated_by` VARCHAR(255),
    `cus_updated_date` DATETIME(6),
    `cus_version` VARCHAR(50),
    `cus_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `customers_cus_customer_number_key` UNIQUE (`cus_customer_number`),
    CONSTRAINT `customers_cus_user_id_key` UNIQUE (`cus_user_id`),
    CONSTRAINT `customers_pkey` PRIMARY KEY (`cus_customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `customer_details` (
    `cud_detail_id` BIGINT NOT NULL AUTO_INCREMENT,
    `cud_customer_id` BIGINT NOT NULL,
    `cud_full_name` VARCHAR(255) NOT NULL,
    `cud_date_of_birth` DATE,
    `cud_father_or_spouse_name` VARCHAR(255),
    `cud_occupation` VARCHAR(255),
    `cud_created_by` VARCHAR(255),
    `cud_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `cud_updated_by` VARCHAR(255),
    `cud_updated_date` DATETIME(6),
    `cud_version` VARCHAR(50),
    `cud_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `customer_details_pkey` PRIMARY KEY (`cud_detail_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `customer_addresses` (
    `cad_address_id` BIGINT NOT NULL AUTO_INCREMENT,
    `cad_customer_id` BIGINT NOT NULL,
    `cad_address_type` VARCHAR(30),
    `cad_city` VARCHAR(255),
    `cad_postal_code` VARCHAR(20),
    `cad_is_primary` TINYINT(1) DEFAULT false NOT NULL,
    `cad_is_verified` TINYINT(1) DEFAULT false NOT NULL,
    `cad_verified_by` BIGINT,
    `cad_created_by` VARCHAR(255),
    `cad_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `cad_updated_by` VARCHAR(255),
    `cad_updated_date` DATETIME(6),
    `cad_version` VARCHAR(50),
    `cad_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `customer_addresses_pkey` PRIMARY KEY (`cad_address_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `customer_emails` (
    `cem_email_id` BIGINT NOT NULL AUTO_INCREMENT,
    `cem_customer_id` BIGINT NOT NULL,
    `cem_contact_type` VARCHAR(30),
    `cem_email_address` VARCHAR(255) NOT NULL,
    `cem_is_primary_signup` TINYINT(1) DEFAULT false NOT NULL,
    `cem_is_primary` TINYINT(1) DEFAULT false NOT NULL,
    `cem_is_verified` TINYINT(1) DEFAULT false NOT NULL,
    `cem_verification_method` VARCHAR(30),
    `cem_consent_marketing` TINYINT(1) DEFAULT false NOT NULL,
    `cem_created_by` VARCHAR(255),
    `cem_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `cem_updated_by` VARCHAR(255),
    `cem_updated_date` DATETIME(6),
    `cem_version` VARCHAR(50),
    `cem_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `customer_emails_pkey` PRIMARY KEY (`cem_email_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `customer_phone_numbers` (
    `cph_phone_id` BIGINT NOT NULL AUTO_INCREMENT,
    `cph_customer_id` BIGINT NOT NULL,
    `cph_contact_type` VARCHAR(30),
    `cph_country_code` VARCHAR(10),
    `cph_phone_number` VARCHAR(30) NOT NULL,
    `cph_is_primary_signup` TINYINT(1) DEFAULT false NOT NULL,
    `cph_is_primary` TINYINT(1) DEFAULT false NOT NULL,
    `cph_is_verified` TINYINT(1) DEFAULT false NOT NULL,
    `cph_verification_method` VARCHAR(30),
    `cph_consent_marketing` TINYINT(1) DEFAULT false NOT NULL,
    `cph_created_by` VARCHAR(255),
    `cph_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `cph_updated_by` VARCHAR(255),
    `cph_updated_date` DATETIME(6),
    `cph_version` VARCHAR(50),
    `cph_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `customer_phone_numbers_pkey` PRIMARY KEY (`cph_phone_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `customer_identity_proofs` (
    `cip_proof_id` BIGINT NOT NULL AUTO_INCREMENT,
    `cip_customer_id` BIGINT NOT NULL,
    `cip_proof_type` BIGINT NOT NULL,
    `cip_proof_value` VARCHAR(255) NOT NULL,
    `cip_proof_value_masked` VARCHAR(255),
    `cip_expiry_date` DATE,
    `cip_verification_status` VARCHAR(30) DEFAULT 'PENDING' NOT NULL,
    `cip_verified_by` BIGINT,
    `cip_document_ref` VARCHAR(255),
    `cip_created_by` VARCHAR(255),
    `cip_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `cip_updated_by` VARCHAR(255),
    `cip_updated_date` DATETIME(6),
    `cip_version` VARCHAR(50),
    `cip_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `customer_identity_proofs_pkey` PRIMARY KEY (`cip_proof_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `customer_classification_types` (
    `cct_type_id` BIGINT NOT NULL AUTO_INCREMENT,
    `cct_type_code` VARCHAR(50) NOT NULL,
    `cct_type_name` VARCHAR(255) NOT NULL,
    `cct_is_active` TINYINT(1) DEFAULT true NOT NULL,
    `cct_validation_regex` VARCHAR(500),
    `cct_requires_photo` TINYINT(1) DEFAULT false NOT NULL,
    `cct_created_by` VARCHAR(255),
    `cct_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `cct_updated_by` VARCHAR(255),
    `cct_updated_date` DATETIME(6),
    `cct_version` VARCHAR(50),
    `cct_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `customer_classification_types_cct_type_code_key` UNIQUE (`cct_type_code`),
    CONSTRAINT `customer_classification_types_pkey` PRIMARY KEY (`cct_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `branches` (
    `bra_branch_id` BIGINT NOT NULL AUTO_INCREMENT,
    `bra_branch_code` VARCHAR(50) NOT NULL,
    `bra_branch_name` VARCHAR(255) NOT NULL,
    `bra_city` VARCHAR(255),
    `bra_is_active` TINYINT(1) DEFAULT true NOT NULL,
    `bra_created_by` VARCHAR(255),
    `bra_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `bra_updated_by` VARCHAR(255),
    `bra_updated_date` DATETIME(6),
    `bra_version` VARCHAR(50),
    `bra_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    CONSTRAINT `branches_bra_branch_code_key` UNIQUE (`bra_branch_code`),
    CONSTRAINT `branches_pkey` PRIMARY KEY (`bra_branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `employee_profile` (
    `emp_employee_profile_id` BIGINT NOT NULL AUTO_INCREMENT,
    `emp_user_id` BIGINT NOT NULL,
    `emp_employee_code` VARCHAR(50) NOT NULL,
    `emp_full_name` VARCHAR(255) NOT NULL,
    `emp_home_branch_id` BIGINT,
    `emp_designation` VARCHAR(255),
    `emp_date_of_joining` DATE,
    `emp_date_of_leaving` DATE,
    `emp_reporting_manager_id` BIGINT,
    `emp_created_by` VARCHAR(255),
    `emp_created_date` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    `emp_updated_by` VARCHAR(255),
    `emp_updated_date` DATETIME(6),
    `emp_version` VARCHAR(50),
    `emp_uuid` VARCHAR(64) DEFAULT (UUID()) NOT NULL,
    `emp_email` VARCHAR(255),
    CONSTRAINT `employee_profile_emp_employee_code_key` UNIQUE (`emp_employee_code`),
    CONSTRAINT `employee_profile_emp_user_id_key` UNIQUE (`emp_user_id`),
    CONSTRAINT `employee_profile_pkey` PRIMARY KEY (`emp_employee_profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE INDEX `idx_cad_customer_id` ON `customer_addresses` (`cad_customer_id`);
CREATE INDEX `idx_cad_verified_by` ON `customer_addresses` (`cad_verified_by`);
CREATE INDEX `idx_cem_customer_id` ON `customer_emails` (`cem_customer_id`);
CREATE INDEX `idx_cip_customer_id` ON `customer_identity_proofs` (`cip_customer_id`);
CREATE INDEX `idx_cip_verified_by` ON `customer_identity_proofs` (`cip_verified_by`);
CREATE INDEX `idx_cph_customer_id` ON `customer_phone_numbers` (`cph_customer_id`);
CREATE INDEX `idx_cud_customer_id` ON `customer_details` (`cud_customer_id`);
CREATE INDEX `idx_cus_classification` ON `customers` (`cus_classification_type_id`);
CREATE INDEX `idx_cus_home_branch` ON `customers` (`cus_home_branch_id`);
CREATE INDEX `idx_emp_home_branch` ON `employee_profile` (`emp_home_branch_id`);
CREATE INDEX `idx_emp_reporting_manager` ON `employee_profile` (`emp_reporting_manager_id`);
CREATE INDEX `idx_refresh_tokens_token` ON `refresh_tokens` (`token`);
CREATE INDEX `idx_refresh_tokens_user` ON `refresh_tokens` (`user_id`);
CREATE INDEX `idx_ucr_user_id` ON `user_credentials` (`ucr_user_id`);
CREATE INDEX `idx_uph_user_id` ON `user_password_history` (`uph_user_id`);
CREATE INDEX `idx_ur_role_id` ON `user_roles` (`ur_role_id`);
CREATE INDEX `idx_ur_user_id` ON `user_roles` (`ur_user_id`);

-- Local FKs are added after every table and index exists.
ALTER TABLE `customer_addresses` ADD CONSTRAINT `customer_addresses_cad_customer_id_fkey` FOREIGN KEY (`cad_customer_id`) REFERENCES `customers`(`cus_customer_id`);
ALTER TABLE `customer_addresses` ADD CONSTRAINT `customer_addresses_cad_verified_by_fkey` FOREIGN KEY (`cad_verified_by`) REFERENCES `users`(`usr_user_id`);
ALTER TABLE `customer_details` ADD CONSTRAINT `customer_details_cud_customer_id_fkey` FOREIGN KEY (`cud_customer_id`) REFERENCES `customers`(`cus_customer_id`);
ALTER TABLE `customer_emails` ADD CONSTRAINT `customer_emails_cem_customer_id_fkey` FOREIGN KEY (`cem_customer_id`) REFERENCES `customers`(`cus_customer_id`);
ALTER TABLE `customer_identity_proofs` ADD CONSTRAINT `customer_identity_proofs_cip_customer_id_fkey` FOREIGN KEY (`cip_customer_id`) REFERENCES `customers`(`cus_customer_id`);
ALTER TABLE `customer_identity_proofs` ADD CONSTRAINT `customer_identity_proofs_cip_proof_type_fkey` FOREIGN KEY (`cip_proof_type`) REFERENCES `customer_classification_types`(`cct_type_id`);
ALTER TABLE `customer_identity_proofs` ADD CONSTRAINT `customer_identity_proofs_cip_verified_by_fkey` FOREIGN KEY (`cip_verified_by`) REFERENCES `users`(`usr_user_id`);
ALTER TABLE `customer_phone_numbers` ADD CONSTRAINT `customer_phone_numbers_cph_customer_id_fkey` FOREIGN KEY (`cph_customer_id`) REFERENCES `customers`(`cus_customer_id`);
ALTER TABLE `customers` ADD CONSTRAINT `customers_cus_classification_type_id_fkey` FOREIGN KEY (`cus_classification_type_id`) REFERENCES `customer_classification_types`(`cct_type_id`);
ALTER TABLE `customers` ADD CONSTRAINT `customers_cus_home_branch_id_fkey` FOREIGN KEY (`cus_home_branch_id`) REFERENCES `branches`(`bra_branch_id`);
ALTER TABLE `customers` ADD CONSTRAINT `customers_cus_user_id_fkey` FOREIGN KEY (`cus_user_id`) REFERENCES `users`(`usr_user_id`);
ALTER TABLE `employee_profile` ADD CONSTRAINT `employee_profile_emp_home_branch_id_fkey` FOREIGN KEY (`emp_home_branch_id`) REFERENCES `branches`(`bra_branch_id`);
ALTER TABLE `employee_profile` ADD CONSTRAINT `employee_profile_emp_reporting_manager_id_fkey` FOREIGN KEY (`emp_reporting_manager_id`) REFERENCES `users`(`usr_user_id`);
ALTER TABLE `employee_profile` ADD CONSTRAINT `employee_profile_emp_user_id_fkey` FOREIGN KEY (`emp_user_id`) REFERENCES `users`(`usr_user_id`);
ALTER TABLE `refresh_tokens` ADD CONSTRAINT `refresh_tokens_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`usr_user_id`) ON DELETE CASCADE;
ALTER TABLE `user_credentials` ADD CONSTRAINT `user_credentials_ucr_user_id_fkey` FOREIGN KEY (`ucr_user_id`) REFERENCES `users`(`usr_user_id`);
ALTER TABLE `user_password_history` ADD CONSTRAINT `user_password_history_uph_user_id_fkey` FOREIGN KEY (`uph_user_id`) REFERENCES `users`(`usr_user_id`);
ALTER TABLE `user_roles` ADD CONSTRAINT `user_roles_ur_role_id_fkey` FOREIGN KEY (`ur_role_id`) REFERENCES `roles`(`rol_role_id`);
ALTER TABLE `user_roles` ADD CONSTRAINT `user_roles_ur_user_id_fkey` FOREIGN KEY (`ur_user_id`) REFERENCES `users`(`usr_user_id`);

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

