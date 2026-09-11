CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS public; -- for gen_random_uuid()

CREATE TABLE public.branches (
    bra_branch_id       SERIAL PRIMARY KEY,
    bra_branch_code     VARCHAR(50) NOT NULL UNIQUE,
    bra_branch_name     VARCHAR(255) NOT NULL,
    bra_city            VARCHAR(255),
    bra_is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    bra_created_by      VARCHAR(255),
    bra_created_date    TIMESTAMPTZ NOT NULL DEFAULT now(),
    bra_updated_by      VARCHAR(255),
    bra_updated_date    TIMESTAMPTZ,
    bra_version         VARCHAR(50),
    bra_uuid            VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    bra_ldbid           VARCHAR(64),
    bra_host_ts         TIMESTAMPTZ,
    bra_local_ts        TIMESTAMPTZ,
    bra_rule_system_id  INT,
    bra_crud_value      VARCHAR(10),
    bra_workstation_id  INT
);

CREATE TABLE public.roles (
    rol_role_id         SERIAL PRIMARY KEY,
    rol_role_code       VARCHAR(50) NOT NULL UNIQUE,
    rol_role_name       VARCHAR(255) NOT NULL,
    rol_description     VARCHAR(500),
    rol_created_by      VARCHAR(255),
    rol_created_date    TIMESTAMPTZ NOT NULL DEFAULT now(),
    rol_updated_by      VARCHAR(255),
    rol_updated_date    TIMESTAMPTZ,
    rol_version         VARCHAR(50),
    rol_uuid            VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    rol_ldbid           VARCHAR(64),
    rol_host_ts         TIMESTAMPTZ,
    rol_local_ts        TIMESTAMPTZ,
    rol_rule_system_id  INT,
    rol_crud_value      VARCHAR(10),
    rol_workstation_id  INT
);

CREATE TABLE public.users (
    usr_user_id         SERIAL PRIMARY KEY,
    usr_user_uuid       VARCHAR(64) NOT NULL UNIQUE DEFAULT gen_random_uuid()::text,
    usr_user_type       VARCHAR(30) NOT NULL,
    usr_status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    usr_created_by      VARCHAR(255),
    usr_created_date    TIMESTAMPTZ NOT NULL DEFAULT now(),
    usr_updated_by      VARCHAR(255),
    usr_updated_date    TIMESTAMPTZ,
    usr_version         VARCHAR(50),
    usr_uuid            VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    usr_ldbid           VARCHAR(64),
    usr_host_ts         TIMESTAMPTZ,
    usr_local_ts        TIMESTAMPTZ,
    usr_rule_system_id  INT,
    usr_crud_value      VARCHAR(10),
    usr_workstation_id  INT
);

CREATE TABLE public.employee_profile (
    emp_employee_profile_id  SERIAL PRIMARY KEY,
    emp_user_id              INT NOT NULL UNIQUE REFERENCES public.users(usr_user_id),
    emp_employee_code        VARCHAR(50) NOT NULL UNIQUE,
    emp_full_name            VARCHAR(255) NOT NULL,
    emp_home_branch_id       INT REFERENCES public.branches(bra_branch_id),
    emp_designation          VARCHAR(255),
    emp_date_of_joining      DATE,
    emp_date_of_leaving      DATE,
    emp_reporting_manager_id INT REFERENCES public.users(usr_user_id),
    emp_created_by           VARCHAR(255),
    emp_created_date         TIMESTAMPTZ NOT NULL DEFAULT now(),
    emp_updated_by           VARCHAR(255),
    emp_updated_date         TIMESTAMPTZ,
    emp_version              VARCHAR(50),
    emp_uuid                 VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    emp_ldbid                VARCHAR(64),
    emp_host_ts              TIMESTAMPTZ,
    emp_local_ts             TIMESTAMPTZ,
    emp_rule_system_id       INT,
    emp_crud_value           VARCHAR(10),
    emp_workstation_id       INT
);
CREATE INDEX idx_emp_home_branch ON public.employee_profile(emp_home_branch_id);
CREATE INDEX idx_emp_reporting_manager ON public.employee_profile(emp_reporting_manager_id);

CREATE TABLE public.user_credentials (
    ucr_credential_id           SERIAL PRIMARY KEY,
    ucr_user_id                 INT NOT NULL REFERENCES public.users(usr_user_id),
    ucr_credential_type         VARCHAR(30) NOT NULL DEFAULT 'PASSWORD',
    ucr_username                VARCHAR(255) NOT NULL UNIQUE,
    ucr_password_hash           VARCHAR(255) NOT NULL,
    ucr_must_change_password    BOOLEAN NOT NULL DEFAULT FALSE,
    ucr_failed_attempt_count    INT NOT NULL DEFAULT 0,
    ucr_locked_until            TIMESTAMPTZ,
    ucr_mfa_enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    ucr_password_changed_at     TIMESTAMPTZ,
    ucr_last_login_at           TIMESTAMPTZ,
    ucr_last_login_ip           VARCHAR(64),
    ucr_created_by              VARCHAR(255),
    ucr_created_date            TIMESTAMPTZ NOT NULL DEFAULT now(),
    ucr_updated_by              VARCHAR(255),
    ucr_updated_date            TIMESTAMPTZ,
    ucr_version                 VARCHAR(50),
    ucr_uuid                    VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    ucr_ldbid                   VARCHAR(64),
    ucr_host_ts                 TIMESTAMPTZ,
    ucr_local_ts                TIMESTAMPTZ,
    ucr_rule_system_id          INT,
    ucr_crud_value              VARCHAR(10),
    ucr_workstation_id          INT
);
CREATE INDEX idx_ucr_user_id ON public.user_credentials(ucr_user_id);

CREATE TABLE public.user_password_history (
    uph_history_id      SERIAL PRIMARY KEY,
    uph_user_id         INT NOT NULL REFERENCES public.users(usr_user_id),
    uph_password_hash   VARCHAR(255) NOT NULL,
    uph_changed_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    uph_created_by      VARCHAR(255),
    uph_created_date    TIMESTAMPTZ NOT NULL DEFAULT now(),
    uph_updated_by      VARCHAR(255),
    uph_updated_date    TIMESTAMPTZ,
    uph_version         VARCHAR(50),
    uph_uuid            VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    uph_ldbid           VARCHAR(64),
    uph_host_ts         TIMESTAMPTZ,
    uph_local_ts        TIMESTAMPTZ,
    uph_rule_system_id  INT,
    uph_crud_value      VARCHAR(10),
    uph_workstation_id  INT
);
CREATE INDEX idx_uph_user_id ON public.user_password_history(uph_user_id);

CREATE TABLE public.user_roles (
    ur_user_role_id     SERIAL PRIMARY KEY,
    ur_user_id          INT NOT NULL REFERENCES public.users(usr_user_id),
    ur_role_id          INT NOT NULL REFERENCES public.roles(rol_role_id),
    ur_assigned_date    TIMESTAMPTZ NOT NULL DEFAULT now(),
    ur_created_by       VARCHAR(255),
    ur_created_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
    ur_updated_by       VARCHAR(255),
    ur_updated_date     TIMESTAMPTZ,
    ur_version          VARCHAR(50),
    ur_uuid             VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    ur_ldbid            VARCHAR(64),
    ur_host_ts          TIMESTAMPTZ,
    ur_local_ts         TIMESTAMPTZ,
    ur_rule_system_id   INT,
    ur_crud_value       VARCHAR(10),
    ur_workstation_id   INT,
    UNIQUE (ur_user_id, ur_role_id)
);
CREATE INDEX idx_ur_user_id ON public.user_roles(ur_user_id);
CREATE INDEX idx_ur_role_id ON public.user_roles(ur_role_id);

CREATE TABLE public.customer_classification_types (
    cct_type_id           SERIAL PRIMARY KEY,
    cct_type_code         VARCHAR(50) NOT NULL UNIQUE,
    cct_type_name         VARCHAR(255) NOT NULL,
    cct_is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    cct_validation_regex  VARCHAR(500),
    cct_requires_photo    BOOLEAN NOT NULL DEFAULT FALSE,
    cct_created_by        VARCHAR(255),
    cct_created_date      TIMESTAMPTZ NOT NULL DEFAULT now(),
    cct_updated_by        VARCHAR(255),
    cct_updated_date      TIMESTAMPTZ,
    cct_version           VARCHAR(50),
    cct_uuid              VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    cct_ldbid             VARCHAR(64),
    cct_host_ts           TIMESTAMPTZ,
    cct_local_ts          TIMESTAMPTZ,
    cct_rule_system_id    INT,
    cct_crud_value        VARCHAR(10),
    cct_workstation_id    INT
);

CREATE TABLE public.customers (
    cus_customer_id             SERIAL PRIMARY KEY,
    cus_user_id                 INT NOT NULL UNIQUE REFERENCES public.users(usr_user_id),
    cus_customer_number         VARCHAR(50) NOT NULL UNIQUE,
    cus_legal_entity_type       VARCHAR(30),
    cus_home_branch_id          INT REFERENCES public.branches(bra_branch_id),
    cus_kyc_status               VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    cus_risk_category            VARCHAR(30),
    cus_signup_contact_method    VARCHAR(30),
    cus_classification_type_id   INT REFERENCES public.customer_classification_types(cct_type_id),
    cus_created_by                VARCHAR(255),
    cus_created_date              TIMESTAMPTZ NOT NULL DEFAULT now(),
    cus_updated_by                VARCHAR(255),
    cus_updated_date               TIMESTAMPTZ,
    cus_version                    VARCHAR(50),
    cus_uuid                       VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    cus_ldbid                      VARCHAR(64),
    cus_host_ts                    TIMESTAMPTZ,
    cus_local_ts                   TIMESTAMPTZ,
    cus_rule_system_id             INT,
    cus_crud_value                 VARCHAR(10),
    cus_workstation_id             INT
);
CREATE INDEX idx_cus_home_branch ON public.customers(cus_home_branch_id);
CREATE INDEX idx_cus_classification ON public.customers(cus_classification_type_id);

CREATE TABLE public.customer_details (
    cud_detail_id                 SERIAL PRIMARY KEY,
    cud_customer_id                INT NOT NULL REFERENCES public.customers(cus_customer_id),
    cud_full_name                   VARCHAR(255) NOT NULL,
    cud_date_of_birth                DATE,
    cud_father_or_spouse_name         VARCHAR(255),
    cud_occupation                    VARCHAR(255),
    cud_created_by                    VARCHAR(255),
    cud_created_date                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    cud_updated_by                    VARCHAR(255),
    cud_updated_date                  TIMESTAMPTZ,
    cud_version                       VARCHAR(50),
    cud_uuid                          VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    cud_ldbid                         VARCHAR(64),
    cud_host_ts                       TIMESTAMPTZ,
    cud_local_ts                      TIMESTAMPTZ,
    cud_rule_system_id                INT,
    cud_crud_value                    VARCHAR(10),
    cud_workstation_id                INT
);
CREATE INDEX idx_cud_customer_id ON public.customer_details(cud_customer_id);

CREATE TABLE public.customer_identity_proofs (
    cip_proof_id                SERIAL PRIMARY KEY,
    cip_customer_id               INT NOT NULL REFERENCES public.customers(cus_customer_id),
    cip_proof_type                 INT NOT NULL REFERENCES public.customer_classification_types(cct_type_id),
    cip_proof_value                  VARCHAR(255) NOT NULL,
    cip_proof_value_masked             VARCHAR(255),
    cip_expiry_date                    DATE,
    cip_verification_status             VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    cip_verified_by                      INT REFERENCES public.users(usr_user_id),
    cip_document_ref                      VARCHAR(255),
    cip_created_by                        VARCHAR(255),
    cip_created_date                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    cip_updated_by                        VARCHAR(255),
    cip_updated_date                      TIMESTAMPTZ,
    cip_version                           VARCHAR(50),
    cip_uuid                              VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    cip_ldbid                             VARCHAR(64),
    cip_host_ts                           TIMESTAMPTZ,
    cip_local_ts                          TIMESTAMPTZ,
    cip_rule_system_id                    INT,
    cip_crud_value                        VARCHAR(10),
    cip_workstation_id                    INT
);
CREATE INDEX idx_cip_customer_id ON public.customer_identity_proofs(cip_customer_id);
CREATE INDEX idx_cip_verified_by ON public.customer_identity_proofs(cip_verified_by);

CREATE TABLE public.customer_addresses (
    cad_address_id           SERIAL PRIMARY KEY,
    cad_customer_id            INT NOT NULL REFERENCES public.customers(cus_customer_id),
    cad_address_type             VARCHAR(30),
    cad_city                       VARCHAR(255),
    cad_postal_code                 VARCHAR(20),
    cad_is_primary                   BOOLEAN NOT NULL DEFAULT FALSE,
    cad_is_verified                   BOOLEAN NOT NULL DEFAULT FALSE,
    cad_verified_by                     INT REFERENCES public.users(usr_user_id),
    cad_created_by                       VARCHAR(255),
    cad_created_date                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    cad_updated_by                       VARCHAR(255),
    cad_updated_date                     TIMESTAMPTZ,
    cad_version                          VARCHAR(50),
    cad_uuid                             VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    cad_ldbid                            VARCHAR(64),
    cad_host_ts                          TIMESTAMPTZ,
    cad_local_ts                         TIMESTAMPTZ,
    cad_rule_system_id                   INT,
    cad_crud_value                       VARCHAR(10),
    cad_workstation_id                   INT
);
CREATE INDEX idx_cad_customer_id ON public.customer_addresses(cad_customer_id);
CREATE INDEX idx_cad_verified_by ON public.customer_addresses(cad_verified_by);

CREATE TABLE public.customer_phone_numbers (
    cph_phone_id              SERIAL PRIMARY KEY,
    cph_customer_id              INT NOT NULL REFERENCES public.customers(cus_customer_id),
    cph_contact_type               VARCHAR(30),
    cph_country_code                 VARCHAR(10),
    cph_phone_number                   VARCHAR(30) NOT NULL,
    cph_is_primary_signup                BOOLEAN NOT NULL DEFAULT FALSE,
    cph_is_primary                         BOOLEAN NOT NULL DEFAULT FALSE,
    cph_is_verified                         BOOLEAN NOT NULL DEFAULT FALSE,
    cph_verification_method                   VARCHAR(30),
    cph_consent_marketing                       BOOLEAN NOT NULL DEFAULT FALSE,
    cph_created_by                               VARCHAR(255),
    cph_created_date                             TIMESTAMPTZ NOT NULL DEFAULT now(),
    cph_updated_by                               VARCHAR(255),
    cph_updated_date                             TIMESTAMPTZ,
    cph_version                                  VARCHAR(50),
    cph_uuid                                     VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    cph_ldbid                                    VARCHAR(64),
    cph_host_ts                                  TIMESTAMPTZ,
    cph_local_ts                                 TIMESTAMPTZ,
    cph_rule_system_id                           INT,
    cph_crud_value                               VARCHAR(10),
    cph_workstation_id                           INT
);
CREATE INDEX idx_cph_customer_id ON public.customer_phone_numbers(cph_customer_id);

CREATE TABLE public.customer_emails (
    cem_email_id              SERIAL PRIMARY KEY,
    cem_customer_id              INT NOT NULL REFERENCES public.customers(cus_customer_id),
    cem_contact_type               VARCHAR(30),
    cem_email_address                 VARCHAR(255) NOT NULL,
    cem_is_primary_signup               BOOLEAN NOT NULL DEFAULT FALSE,
    cem_is_primary                        BOOLEAN NOT NULL DEFAULT FALSE,
    cem_is_verified                        BOOLEAN NOT NULL DEFAULT FALSE,
    cem_verification_method                  VARCHAR(30),
    cem_consent_marketing                      BOOLEAN NOT NULL DEFAULT FALSE,
    cem_created_by                              VARCHAR(255),
    cem_created_date                            TIMESTAMPTZ NOT NULL DEFAULT now(),
    cem_updated_by                              VARCHAR(255),
    cem_updated_date                            TIMESTAMPTZ,
    cem_version                                 VARCHAR(50),
    cem_uuid                                    VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    cem_ldbid                                   VARCHAR(64),
    cem_host_ts                                 TIMESTAMPTZ,
    cem_local_ts                                TIMESTAMPTZ,
    cem_rule_system_id                          INT,
    cem_crud_value                              VARCHAR(10),
    cem_workstation_id                          INT
);
CREATE INDEX idx_cem_customer_id ON public.customer_emails(cem_customer_id);

CREATE TABLE public.product_category (
    pc_category_id        SERIAL PRIMARY KEY,
    pc_category_code      VARCHAR(50) NOT NULL UNIQUE,
    pc_category_name      VARCHAR(255) NOT NULL,
    pc_parent_category_id INT REFERENCES public.product_category(pc_category_id),
    pc_created_by         VARCHAR(255),
    pc_created_date       TIMESTAMPTZ NOT NULL DEFAULT now(),
    pc_updated_by         VARCHAR(255),
    pc_updated_date       TIMESTAMPTZ,
    pc_version            VARCHAR(50),
    pc_uuid               VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    pc_ldbid              VARCHAR(64),
    pc_host_ts            TIMESTAMPTZ,
    pc_local_ts           TIMESTAMPTZ,
    pc_rule_system_id     INT,
    pc_crud_value         VARCHAR(10),
    pc_workstation_id     INT
);
CREATE INDEX idx_pc_parent_category ON public.product_category(pc_parent_category_id);

CREATE TABLE public.rule_type (
    rut_rule_type_id    SERIAL PRIMARY KEY,
    rut_rule_type_code  VARCHAR(50) NOT NULL UNIQUE,
    rut_description     VARCHAR(500),
    rut_created_by      VARCHAR(255),
    rut_created_date    TIMESTAMPTZ NOT NULL DEFAULT now(),
    rut_updated_by      VARCHAR(255),
    rut_updated_date    TIMESTAMPTZ,
    rut_version         VARCHAR(50),
    rut_uuid            VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    rut_ldbid           VARCHAR(64),
    rut_host_ts         TIMESTAMPTZ,
    rut_local_ts        TIMESTAMPTZ,
    rut_rule_system_id  INT,
    rut_crud_value      VARCHAR(10),
    rut_workstation_id  INT
);

CREATE TABLE public.currency (
    cur_currency_id       SERIAL PRIMARY KEY,
    cur_iso_code          VARCHAR(10) NOT NULL UNIQUE,
    cur_iso_numeric_code  VARCHAR(10),
    cur_currency_name     VARCHAR(255) NOT NULL,
    cur_currency_symbol   VARCHAR(10),
    cur_decimal_places    INT,
    cur_is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    cur_created_by        VARCHAR(255),
    cur_created_date      TIMESTAMPTZ NOT NULL DEFAULT now(),
    cur_updated_by        VARCHAR(255),
    cur_updated_date      TIMESTAMPTZ,
    cur_version           VARCHAR(50),
    cur_uuid              VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    cur_ldbid             VARCHAR(64),
    cur_host_ts           TIMESTAMPTZ,
    cur_local_ts          TIMESTAMPTZ,
    cur_rule_system_id    INT,
    cur_crud_value        VARCHAR(10),
    cur_workstation_id    INT
);

CREATE TABLE public.role_type (
    rot_role_type_id    SERIAL PRIMARY KEY,
    rot_role_code       VARCHAR(50) NOT NULL UNIQUE,
    rot_description     VARCHAR(500),
    rot_created_by      VARCHAR(255),
    rot_created_date    TIMESTAMPTZ NOT NULL DEFAULT now(),
    rot_updated_by      VARCHAR(255),
    rot_updated_date    TIMESTAMPTZ,
    rot_version         VARCHAR(50),
    rot_uuid            VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    rot_ldbid           VARCHAR(64),
    rot_host_ts         TIMESTAMPTZ,
    rot_local_ts        TIMESTAMPTZ,
    rot_rule_system_id  INT,
    rot_crud_value      VARCHAR(10),
    rot_workstation_id  INT
);

CREATE TABLE public.product (
    pro_product_id         SERIAL PRIMARY KEY,
    pro_product_code       VARCHAR(50) NOT NULL UNIQUE,
    pro_product_name       VARCHAR(255) NOT NULL,
    pro_category_id        INT NOT NULL REFERENCES public.product_category(pc_category_id),
    pro_base_currency_code VARCHAR(10) REFERENCES public.currency(cur_iso_code),
    pro_status             VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    pro_effective_from     VARCHAR(30),
    pro_effective_to       VARCHAR(30),
    pro_description        VARCHAR(1000),
    pro_created_by         VARCHAR(255),
    pro_created_date       TIMESTAMPTZ NOT NULL DEFAULT now(),
    pro_updated_by         VARCHAR(255),
    pro_updated_date       TIMESTAMPTZ,
    pro_version            VARCHAR(50),
    pro_uuid               VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    pro_ldbid              VARCHAR(64),
    pro_host_ts            TIMESTAMPTZ,
    pro_local_ts           TIMESTAMPTZ,
    pro_rule_system_id     INT,
    pro_crud_value         VARCHAR(10),
    pro_workstation_id     INT
);
CREATE INDEX idx_pro_category ON public.product(pro_category_id);
CREATE INDEX idx_pro_base_currency ON public.product(pro_base_currency_code);

CREATE TABLE public.attribute_definitions (
    ad_attribute_def_id SERIAL PRIMARY KEY,
    ad_category_id      INT NOT NULL REFERENCES public.product_category(pc_category_id),
    ad_attribute_key    VARCHAR(100) NOT NULL,
    ad_data_type        VARCHAR(30) NOT NULL,
    ad_display_name     VARCHAR(255),
    ad_is_required      BOOLEAN NOT NULL DEFAULT FALSE,
    ad_created_by       VARCHAR(255),
    ad_created_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
    ad_updated_by       VARCHAR(255),
    ad_updated_date     TIMESTAMPTZ,
    ad_version          VARCHAR(50),
    ad_uuid             VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    ad_ldbid            VARCHAR(64),
    ad_host_ts          TIMESTAMPTZ,
    ad_local_ts         TIMESTAMPTZ,
    ad_rule_system_id   INT,
    ad_crud_value       VARCHAR(10),
    ad_workstation_id   INT
);
CREATE INDEX idx_ad_category ON public.attribute_definitions(ad_category_id);

CREATE TABLE public.product_rule (
    pr_rule_id          SERIAL PRIMARY KEY,
    pr_product_id       INT NOT NULL REFERENCES public.product(pro_product_id),
    pr_rule_type_id     INT NOT NULL REFERENCES public.rule_type(rut_rule_type_id),
    pr_rule_name        VARCHAR(255) NOT NULL,
    pr_priority         INT,
    pr_combine_logic    VARCHAR(30),
    pr_is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    pr_effective_from   VARCHAR(30),
    pr_effective_to     VARCHAR(30),
    pr_created_by       VARCHAR(255),
    pr_created_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
    pr_updated_by       VARCHAR(255),
    pr_updated_date     TIMESTAMPTZ,
    pr_version          VARCHAR(50),
    pr_uuid             VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    pr_ldbid            VARCHAR(64),
    pr_host_ts          TIMESTAMPTZ,
    pr_local_ts         TIMESTAMPTZ,
    pr_rule_system_id   INT,
    pr_crud_value       VARCHAR(10),
    pr_workstation_id   INT
);
CREATE INDEX idx_pr_product ON public.product_rule(pr_product_id);
CREATE INDEX idx_pr_rule_type ON public.product_rule(pr_rule_type_id);

CREATE TABLE public.product_currency_config (
    pcc_config_id          SERIAL PRIMARY KEY,
    pcc_product_id         INT NOT NULL REFERENCES public.product(pro_product_id),
    pcc_currency_id        INT NOT NULL REFERENCES public.currency(cur_currency_id),
    pcc_is_base_currency   BOOLEAN NOT NULL DEFAULT FALSE,
    pcc_wallet_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    pcc_created_by         VARCHAR(255),
    pcc_created_date       TIMESTAMPTZ NOT NULL DEFAULT now(),
    pcc_updated_by         VARCHAR(255),
    pcc_updated_date       TIMESTAMPTZ,
    pcc_version            VARCHAR(50),
    pcc_uuid               VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    pcc_ldbid              VARCHAR(64),
    pcc_host_ts            TIMESTAMPTZ,
    pcc_local_ts           TIMESTAMPTZ,
    pcc_rule_system_id     INT,
    pcc_crud_value         VARCHAR(10),
    pcc_workstation_id     INT
);
CREATE INDEX idx_pcc_product ON public.product_currency_config(pcc_product_id);
CREATE INDEX idx_pcc_currency ON public.product_currency_config(pcc_currency_id);

CREATE TABLE public.charge_config (
    cc_charge_id           SERIAL PRIMARY KEY,
    cc_product_id          INT NOT NULL REFERENCES public.product(pro_product_id),
    cc_charge_code         VARCHAR(50) NOT NULL,
    cc_calculation_basis   VARCHAR(30),
    cc_gl_code             VARCHAR(50),
    cc_is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    cc_created_by          VARCHAR(255),
    cc_created_date        TIMESTAMPTZ NOT NULL DEFAULT now(),
    cc_updated_by          VARCHAR(255),
    cc_updated_date        TIMESTAMPTZ,
    cc_version             VARCHAR(50),
    cc_uuid                VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    cc_ldbid               VARCHAR(64),
    cc_host_ts             TIMESTAMPTZ,
    cc_local_ts            TIMESTAMPTZ,
    cc_rule_system_id      INT,
    cc_crud_value          VARCHAR(10),
    cc_workstation_id      INT
);
CREATE INDEX idx_cc_product ON public.charge_config(cc_product_id);

CREATE TABLE public.sweep_config (
    sc_sweep_id            SERIAL PRIMARY KEY,
    sc_source_product_id   INT NOT NULL REFERENCES public.product(pro_product_id),
    sc_target_product_id   INT NOT NULL REFERENCES public.product(pro_product_id),
    sc_sweep_type          VARCHAR(30),
    sc_threshold_amount    DECIMAL(18,4),
    sc_sweep_multiple      DECIMAL(18,4),
    sc_currency_id         INT NOT NULL REFERENCES public.currency(cur_currency_id),
    sc_created_by          VARCHAR(255),
    sc_created_date        TIMESTAMPTZ NOT NULL DEFAULT now(),
    sc_updated_by          VARCHAR(255),
    sc_updated_date        TIMESTAMPTZ,
    sc_version             VARCHAR(50),
    sc_uuid                VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    sc_ldbid               VARCHAR(64),
    sc_host_ts             TIMESTAMPTZ,
    sc_local_ts            TIMESTAMPTZ,
    sc_rule_system_id      INT,
    sc_crud_value          VARCHAR(10),
    sc_workstation_id      INT
);
CREATE INDEX idx_sc_source_product ON public.sweep_config(sc_source_product_id);
CREATE INDEX idx_sc_target_product ON public.sweep_config(sc_target_product_id);
CREATE INDEX idx_sc_currency ON public.sweep_config(sc_currency_id);

CREATE TABLE public.loan_repayment_schedule (
    lrs_schedule_id      SERIAL PRIMARY KEY,
    lrs_loan_account_no  VARCHAR(50) NOT NULL,
    lrs_product_id       INT NOT NULL REFERENCES public.product(pro_product_id),
    lrs_note             VARCHAR(500),
    lrs_created_by       VARCHAR(255),
    lrs_created_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
    lrs_updated_by       VARCHAR(255),
    lrs_updated_date     TIMESTAMPTZ,
    lrs_version          VARCHAR(50),
    lrs_uuid             VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    lrs_ldbid            VARCHAR(64),
    lrs_host_ts          TIMESTAMPTZ,
    lrs_local_ts         TIMESTAMPTZ,
    lrs_rule_system_id   INT,
    lrs_crud_value       VARCHAR(10),
    lrs_workstation_id   INT
);
CREATE INDEX idx_lrs_product ON public.loan_repayment_schedule(lrs_product_id);

CREATE TABLE public.product_role_config (
    prc_id               SERIAL PRIMARY KEY,
    prc_product_id       INT NOT NULL REFERENCES public.product(pro_product_id),
    prc_role_type_id     INT NOT NULL REFERENCES public.role_type(rot_role_type_id),
    prc_is_mandatory     BOOLEAN NOT NULL DEFAULT FALSE,
    prc_min_count        INT,
    prc_max_count        INT,
    prc_created_by       VARCHAR(255),
    prc_created_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
    prc_updated_by       VARCHAR(255),
    prc_updated_date     TIMESTAMPTZ,
    prc_version          VARCHAR(50),
    prc_uuid             VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    prc_ldbid            VARCHAR(64),
    prc_host_ts          TIMESTAMPTZ,
    prc_local_ts         TIMESTAMPTZ,
    prc_rule_system_id   INT,
    prc_crud_value       VARCHAR(10),
    prc_workstation_id   INT
);
CREATE INDEX idx_prc_product ON public.product_role_config(prc_product_id);
CREATE INDEX idx_prc_role_type ON public.product_role_config(prc_role_type_id);

CREATE TABLE public.product_attribute_values (
    pav_value_id          SERIAL PRIMARY KEY,
    pav_product_id        INT NOT NULL REFERENCES public.product(pro_product_id),
    pav_attribute_def_id  INT NOT NULL REFERENCES public.attribute_definitions(ad_attribute_def_id),
    pav_value_string      VARCHAR(500),
    pav_value_number      DECIMAL(18,4),
    pav_value_date        TIMESTAMPTZ,
    pav_created_by        VARCHAR(255),
    pav_created_date      TIMESTAMPTZ NOT NULL DEFAULT now(),
    pav_updated_by        VARCHAR(255),
    pav_updated_date      TIMESTAMPTZ,
    pav_version           VARCHAR(50),
    pav_uuid              VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    pav_ldbid             VARCHAR(64),
    pav_host_ts           TIMESTAMPTZ,
    pav_local_ts          TIMESTAMPTZ,
    pav_rule_system_id    INT,
    pav_crud_value        VARCHAR(10),
    pav_workstation_id    INT
);
CREATE INDEX idx_pav_product ON public.product_attribute_values(pav_product_id);
CREATE INDEX idx_pav_attribute_def ON public.product_attribute_values(pav_attribute_def_id);

CREATE TABLE public.rule_outcome (
    ro_outcome_id           SERIAL PRIMARY KEY,
    ro_rule_id              INT NOT NULL REFERENCES public.product_rule(pr_rule_id),
    ro_outcome_type         VARCHAR(30),
    ro_outcome_value        VARCHAR(255),
    ro_unit                 VARCHAR(30),
    ro_compounding_basis    VARCHAR(30),
    ro_payout_frequency     VARCHAR(30),
    ro_currency_id          INT REFERENCES public.currency(cur_currency_id),
    ro_created_by           VARCHAR(255),
    ro_created_date         TIMESTAMPTZ NOT NULL DEFAULT now(),
    ro_updated_by           VARCHAR(255),
    ro_updated_date         TIMESTAMPTZ,
    ro_version              VARCHAR(50),
    ro_uuid                 VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    ro_ldbid                VARCHAR(64),
    ro_host_ts              TIMESTAMPTZ,
    ro_local_ts             TIMESTAMPTZ,
    ro_rule_system_id       INT,
    ro_crud_value           VARCHAR(10),
    ro_workstation_id       INT
);
CREATE INDEX idx_ro_rule ON public.rule_outcome(ro_rule_id);
CREATE INDEX idx_ro_currency ON public.rule_outcome(ro_currency_id);

CREATE TABLE public.rule_condition (
    rc_condition_id        SERIAL PRIMARY KEY,
    rc_rule_id             INT NOT NULL REFERENCES public.product_rule(pr_rule_id),
    rc_condition_attribute VARCHAR(100),
    rc_operator            VARCHAR(30),
    rc_value_from          VARCHAR(100),
    rc_value_to            VARCHAR(100),
    rc_value_set           VARCHAR(500),
    rc_created_by          VARCHAR(255),
    rc_created_date        TIMESTAMPTZ NOT NULL DEFAULT now(),
    rc_updated_by          VARCHAR(255),
    rc_updated_date        TIMESTAMPTZ,
    rc_version             VARCHAR(50),
    rc_uuid                VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    rc_ldbid               VARCHAR(64),
    rc_host_ts             TIMESTAMPTZ,
    rc_local_ts            TIMESTAMPTZ,
    rc_rule_system_id      INT,
    rc_crud_value          VARCHAR(10),
    rc_workstation_id      INT
);
CREATE INDEX idx_rc_rule ON public.rule_condition(rc_rule_id);

CREATE TABLE public.charge_slab (
    cs_slab_id           SERIAL PRIMARY KEY,
    cs_charge_id         INT NOT NULL REFERENCES public.charge_config(cc_charge_id),
    cs_slab_attribute    VARCHAR(100),
    cs_value_from        VARCHAR(100),
    cs_value_to          VARCHAR(100),
    cs_charge_amount     DECIMAL(18,4),
    cs_currency_id       INT NOT NULL REFERENCES public.currency(cur_currency_id),
    cs_created_by        VARCHAR(255),
    cs_created_date      TIMESTAMPTZ NOT NULL DEFAULT now(),
    cs_updated_by        VARCHAR(255),
    cs_updated_date      TIMESTAMPTZ,
    cs_version           VARCHAR(50),
    cs_uuid              VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    cs_ldbid             VARCHAR(64),
    cs_host_ts           TIMESTAMPTZ,
    cs_local_ts          TIMESTAMPTZ,
    cs_rule_system_id    INT,
    cs_crud_value        VARCHAR(10),
    cs_workstation_id    INT
);
CREATE INDEX idx_cs_charge ON public.charge_slab(cs_charge_id);
CREATE INDEX idx_cs_currency ON public.charge_slab(cs_currency_id);

CREATE TABLE public.gl_code_mapping (
    gcm_mapping_id       SERIAL PRIMARY KEY,
    gcm_outcome_id       INT NOT NULL REFERENCES public.rule_outcome(ro_outcome_id),
    gcm_gl_code          VARCHAR(50) NOT NULL,
    gcm_created_by       VARCHAR(255),
    gcm_created_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
    gcm_updated_by       VARCHAR(255),
    gcm_updated_date     TIMESTAMPTZ,
    gcm_version          VARCHAR(50),
    gcm_uuid             VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    gcm_ldbid            VARCHAR(64),
    gcm_host_ts          TIMESTAMPTZ,
    gcm_local_ts         TIMESTAMPTZ,
    gcm_rule_system_id   INT,
    gcm_crud_value       VARCHAR(10),
    gcm_workstation_id   INT
);
CREATE INDEX idx_gcm_outcome ON public.gl_code_mapping(gcm_outcome_id);

CREATE TABLE public.fd_product_ref (
    fpr_product_id       SERIAL PRIMARY KEY,
    fpr_product_code     VARCHAR(50) NOT NULL,
    fpr_product_name     VARCHAR(255) NOT NULL,
    fpr_status           VARCHAR(30),
    fpr_created_by       VARCHAR(255),
    fpr_created_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
    fpr_updated_by       VARCHAR(255),
    fpr_updated_date     TIMESTAMPTZ,
    fpr_version          VARCHAR(50),
    fpr_uuid             VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    fpr_ldbid            VARCHAR(64),
    fpr_host_ts          TIMESTAMPTZ,
    fpr_local_ts         TIMESTAMPTZ,
    fpr_rule_system_id   INT,
    fpr_crud_value       VARCHAR(10),
    fpr_workstation_id   INT
);

CREATE TABLE public.fd_currency_ref (
    fcr_currency_id       SERIAL PRIMARY KEY,
    fcr_iso_code          VARCHAR(10) NOT NULL,
    fcr_currency_name     VARCHAR(255),
    fcr_decimal_places    INT,
    fcr_created_by        VARCHAR(255),
    fcr_created_date      TIMESTAMPTZ NOT NULL DEFAULT now(),
    fcr_updated_by        VARCHAR(255),
    fcr_updated_date      TIMESTAMPTZ,
    fcr_version           VARCHAR(50),
    fcr_uuid              VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    fcr_ldbid             VARCHAR(64),
    fcr_host_ts           TIMESTAMPTZ,
    fcr_local_ts          TIMESTAMPTZ,
    fcr_rule_system_id    INT,
    fcr_crud_value        VARCHAR(10),
    fcr_workstation_id    INT
);

CREATE TABLE public.fd_user_ref (
    fur_user_id           SERIAL PRIMARY KEY,
    fur_user_uuid         VARCHAR(64) NOT NULL,
    fur_customer_number   VARCHAR(50),
    fur_full_name         VARCHAR(255),
    fur_user_status       VARCHAR(30),
    fur_created_by        VARCHAR(255),
    fur_created_date      TIMESTAMPTZ NOT NULL DEFAULT now(),
    fur_updated_by        VARCHAR(255),
    fur_updated_date      TIMESTAMPTZ,
    fur_version           VARCHAR(50),
    fur_uuid              VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    fur_ldbid             VARCHAR(64),
    fur_host_ts           TIMESTAMPTZ,
    fur_local_ts          TIMESTAMPTZ,
    fur_rule_system_id    INT,
    fur_crud_value        VARCHAR(10),
    fur_workstation_id    INT
);

CREATE TABLE public.fd_role_type_ref (
    frtr_role_type_id     SERIAL PRIMARY KEY,
    frtr_role_code        VARCHAR(50) NOT NULL,
    frtr_description      VARCHAR(500),
    frtr_created_by       VARCHAR(255),
    frtr_created_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
    frtr_updated_by       VARCHAR(255),
    frtr_updated_date     TIMESTAMPTZ,
    frtr_version          VARCHAR(50),
    frtr_uuid             VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    frtr_ldbid            VARCHAR(64),
    frtr_host_ts          TIMESTAMPTZ,
    frtr_local_ts         TIMESTAMPTZ,
    frtr_rule_system_id   INT,
    frtr_crud_value       VARCHAR(10),
    frtr_workstation_id   INT
);

CREATE TABLE public.fd_rule_outcome_ref (
    fror_outcome_id       SERIAL PRIMARY KEY,
    fror_outcome_type     VARCHAR(30),
    fror_outcome_value    VARCHAR(255),
    fror_unit             VARCHAR(30),
    fror_created_by       VARCHAR(255),
    fror_created_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
    fror_updated_by       VARCHAR(255),
    fror_updated_date     TIMESTAMPTZ,
    fror_version          VARCHAR(50),
    fror_uuid             VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    fror_ldbid            VARCHAR(64),
    fror_host_ts          TIMESTAMPTZ,
    fror_local_ts         TIMESTAMPTZ,
    fror_rule_system_id   INT,
    fror_crud_value       VARCHAR(10),
    fror_workstation_id   INT
);

CREATE TABLE public.transaction_types (
    tt_txn_type_id        SERIAL PRIMARY KEY,
    tt_txn_type_code      VARCHAR(50) NOT NULL UNIQUE,
    tt_txn_type_name      VARCHAR(255) NOT NULL,
    tt_debit_credit       VARCHAR(10),
    tt_requires_approval  BOOLEAN NOT NULL DEFAULT FALSE,
    tt_created_by         VARCHAR(255),
    tt_created_date       TIMESTAMPTZ NOT NULL DEFAULT now(),
    tt_updated_by         VARCHAR(255),
    tt_updated_date       TIMESTAMPTZ,
    tt_version            VARCHAR(50),
    tt_uuid               VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    tt_ldbid              VARCHAR(64),
    tt_host_ts            TIMESTAMPTZ,
    tt_local_ts           TIMESTAMPTZ,
    tt_rule_system_id     INT,
    tt_crud_value         VARCHAR(10),
    tt_workstation_id     INT
);

CREATE TABLE public.fd_accounts (
    fa_account_id               SERIAL PRIMARY KEY,
    fa_account_number           VARCHAR(50) NOT NULL UNIQUE,
    fa_primary_holder_id        INT NOT NULL REFERENCES public.fd_user_ref(fur_user_id),
    fa_product_id               INT NOT NULL REFERENCES public.fd_product_ref(fpr_product_id),
    fa_applied_rate_outcome_id  INT REFERENCES public.fd_rule_outcome_ref(fror_outcome_id),
    fa_principal_amount         DECIMAL(18,4) NOT NULL,
    fa_currency_id              INT NOT NULL REFERENCES public.fd_currency_ref(fcr_currency_id),
    fa_tenure_months            INT NOT NULL,
    fa_interest_rate            DECIMAL(9,4),
    fa_compounding_frequency    VARCHAR(30),
    fa_payout_frequency         VARCHAR(30),
    fa_auto_renewal             BOOLEAN NOT NULL DEFAULT FALSE,
    fa_open_date                TIMESTAMPTZ NOT NULL DEFAULT now(),
    fa_maturity_date            TIMESTAMPTZ,
    fa_maturity_amount          DECIMAL(18,4),
    fa_status                   VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    fa_created_by               VARCHAR(255),
    fa_created_date              TIMESTAMPTZ NOT NULL DEFAULT now(),
    fa_updated_by                VARCHAR(255),
    fa_updated_date               TIMESTAMPTZ,
    fa_version                    VARCHAR(50),
    fa_uuid                       VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    fa_ldbid                      VARCHAR(64),
    fa_host_ts                    TIMESTAMPTZ,
    fa_local_ts                   TIMESTAMPTZ,
    fa_rule_system_id             INT,
    fa_crud_value                 VARCHAR(10),
    fa_workstation_id             INT
);
CREATE INDEX idx_fa_primary_holder ON public.fd_accounts(fa_primary_holder_id);
CREATE INDEX idx_fa_product ON public.fd_accounts(fa_product_id);
CREATE INDEX idx_fa_currency ON public.fd_accounts(fa_currency_id);
CREATE INDEX idx_fa_applied_rate_outcome ON public.fd_accounts(fa_applied_rate_outcome_id);

CREATE TABLE public.fd_transactions (
    ft_transaction_id    SERIAL PRIMARY KEY,
    ft_account_id        INT NOT NULL REFERENCES public.fd_accounts(fa_account_id),
    ft_txn_type_id       INT NOT NULL REFERENCES public.transaction_types(tt_txn_type_id),
    ft_amount            DECIMAL(18,4) NOT NULL,
    ft_txn_date          TIMESTAMPTZ NOT NULL DEFAULT now(),
    ft_balance_after     DECIMAL(18,4),
    ft_currency_id       INT NOT NULL REFERENCES public.fd_currency_ref(fcr_currency_id),
    ft_remarks           VARCHAR(500),
    ft_created_by        VARCHAR(255),
    ft_created_date      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ft_updated_by        VARCHAR(255),
    ft_updated_date      TIMESTAMPTZ,
    ft_version           VARCHAR(50),
    ft_uuid              VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    ft_ldbid             VARCHAR(64),
    ft_host_ts           TIMESTAMPTZ,
    ft_local_ts          TIMESTAMPTZ,
    ft_rule_system_id    INT,
    ft_crud_value        VARCHAR(10),
    ft_workstation_id    INT
);
CREATE INDEX idx_ft_account ON public.fd_transactions(ft_account_id);
CREATE INDEX idx_ft_txn_type ON public.fd_transactions(ft_txn_type_id);
CREATE INDEX idx_ft_currency ON public.fd_transactions(ft_currency_id);

CREATE TABLE public.fd_notices (
    fn_notice_id          SERIAL PRIMARY KEY,
    fn_account_id         INT NOT NULL REFERENCES public.fd_accounts(fa_account_id),
    fn_notice_type        VARCHAR(50),
    fn_notice_date        TIMESTAMPTZ NOT NULL DEFAULT now(),
    fn_channel            VARCHAR(30),
    fn_delivery_status    VARCHAR(30),
    fn_created_by         VARCHAR(255),
    fn_created_date       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fn_updated_by         VARCHAR(255),
    fn_updated_date       TIMESTAMPTZ,
    fn_version            VARCHAR(50),
    fn_uuid               VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    fn_ldbid              VARCHAR(64),
    fn_host_ts            TIMESTAMPTZ,
    fn_local_ts           TIMESTAMPTZ,
    fn_rule_system_id     INT,
    fn_crud_value         VARCHAR(10),
    fn_workstation_id     INT
);
CREATE INDEX idx_fn_account ON public.fd_notices(fn_account_id);

CREATE TABLE public.fd_statements (
    fs_statement_id       SERIAL PRIMARY KEY,
    fs_account_id         INT NOT NULL REFERENCES public.fd_accounts(fa_account_id),
    fs_period_start       TIMESTAMPTZ,
    fs_period_end         TIMESTAMPTZ,
    fs_generated_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
    fs_file_reference     VARCHAR(255),
    fs_created_by         VARCHAR(255),
    fs_created_date       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fs_updated_by         VARCHAR(255),
    fs_updated_date       TIMESTAMPTZ,
    fs_version            VARCHAR(50),
    fs_uuid               VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    fs_ldbid              VARCHAR(64),
    fs_host_ts            TIMESTAMPTZ,
    fs_local_ts           TIMESTAMPTZ,
    fs_rule_system_id     INT,
    fs_crud_value         VARCHAR(10),
    fs_workstation_id     INT
);
CREATE INDEX idx_fs_account ON public.fd_statements(fs_account_id);

CREATE TABLE public.fd_account_events (
    fae_event_id          SERIAL PRIMARY KEY,
    fae_account_id        INT NOT NULL REFERENCES public.fd_accounts(fa_account_id),
    fae_event_type        VARCHAR(50) NOT NULL,
    fae_payload           JSONB,
    fae_is_published      BOOLEAN NOT NULL DEFAULT FALSE,
    fae_created_by        VARCHAR(255),
    fae_created_date      TIMESTAMPTZ NOT NULL DEFAULT now(),
    fae_updated_by        VARCHAR(255),
    fae_updated_date      TIMESTAMPTZ,
    fae_version           VARCHAR(50),
    fae_uuid              VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    fae_ldbid             VARCHAR(64),
    fae_host_ts           TIMESTAMPTZ,
    fae_local_ts          TIMESTAMPTZ,
    fae_rule_system_id    INT,
    fae_crud_value        VARCHAR(10),
    fae_workstation_id    INT
);
CREATE INDEX idx_fae_account ON public.fd_account_events(fae_account_id);
CREATE INDEX idx_fae_is_published ON public.fd_account_events(fae_is_published) WHERE NOT fae_is_published;

CREATE TABLE public.fd_payout_accounts (
    fpa_payout_account_id   SERIAL PRIMARY KEY,
    fpa_account_id          INT NOT NULL REFERENCES public.fd_accounts(fa_account_id),
    fpa_bank_account_number VARCHAR(50) NOT NULL,
    fpa_ifsc_code           VARCHAR(20),
    fpa_account_holder_name VARCHAR(255),
    fpa_is_primary          BOOLEAN NOT NULL DEFAULT FALSE,
    fpa_created_by          VARCHAR(255),
    fpa_created_date        TIMESTAMPTZ NOT NULL DEFAULT now(),
    fpa_updated_by          VARCHAR(255),
    fpa_updated_date        TIMESTAMPTZ,
    fpa_version             VARCHAR(50),
    fpa_uuid                VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    fpa_ldbid               VARCHAR(64),
    fpa_host_ts             TIMESTAMPTZ,
    fpa_local_ts            TIMESTAMPTZ,
    fpa_rule_system_id      INT,
    fpa_crud_value          VARCHAR(10),
    fpa_workstation_id      INT
);
CREATE INDEX idx_fpa_account ON public.fd_payout_accounts(fpa_account_id);

CREATE TABLE public.fd_interest_accrual (
    fia_accrual_id            SERIAL PRIMARY KEY,
    fia_account_id            INT NOT NULL REFERENCES public.fd_accounts(fa_account_id),
    fia_accrual_date          TIMESTAMPTZ NOT NULL,
    fia_accrued_amount        DECIMAL(18,4) NOT NULL,
    fia_cumulative_accrued    DECIMAL(18,4),
    fia_currency_id           INT NOT NULL REFERENCES public.fd_currency_ref(fcr_currency_id),
    fia_created_by            VARCHAR(255),
    fia_created_date          TIMESTAMPTZ NOT NULL DEFAULT now(),
    fia_updated_by            VARCHAR(255),
    fia_updated_date          TIMESTAMPTZ,
    fia_version               VARCHAR(50),
    fia_uuid                  VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    fia_ldbid                 VARCHAR(64),
    fia_host_ts               TIMESTAMPTZ,
    fia_local_ts              TIMESTAMPTZ,
    fia_rule_system_id        INT,
    fia_crud_value            VARCHAR(10),
    fia_workstation_id        INT
);
CREATE INDEX idx_fia_account ON public.fd_interest_accrual(fia_account_id);
CREATE INDEX idx_fia_currency ON public.fd_interest_accrual(fia_currency_id);

CREATE TABLE public.fd_interest_payout (
    fip_payout_id         SERIAL PRIMARY KEY,
    fip_account_id        INT NOT NULL REFERENCES public.fd_accounts(fa_account_id),
    fip_payout_date       TIMESTAMPTZ NOT NULL,
    fip_amount            DECIMAL(18,4) NOT NULL,
    fip_payout_status     VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    fip_currency_id       INT NOT NULL REFERENCES public.fd_currency_ref(fcr_currency_id),
    fip_created_by        VARCHAR(255),
    fip_created_date      TIMESTAMPTZ NOT NULL DEFAULT now(),
    fip_updated_by        VARCHAR(255),
    fip_updated_date      TIMESTAMPTZ,
    fip_version           VARCHAR(50),
    fip_uuid              VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    fip_ldbid             VARCHAR(64),
    fip_host_ts           TIMESTAMPTZ,
    fip_local_ts          TIMESTAMPTZ,
    fip_rule_system_id    INT,
    fip_crud_value        VARCHAR(10),
    fip_workstation_id    INT
);
CREATE INDEX idx_fip_account ON public.fd_interest_payout(fip_account_id);
CREATE INDEX idx_fip_currency ON public.fd_interest_payout(fip_currency_id);

CREATE TABLE public.account_holder_roles (
    ahr_id                   SERIAL PRIMARY KEY,
    ahr_account_id           INT NOT NULL REFERENCES public.fd_accounts(fa_account_id),
    ahr_user_id              INT NOT NULL REFERENCES public.fd_user_ref(fur_user_id),
    ahr_role_type_id         INT NOT NULL REFERENCES public.fd_role_type_ref(frtr_role_type_id),
    ahr_holder_sequence      INT,
    ahr_operating_mandate    VARCHAR(50),
    ahr_tds_applicable       BOOLEAN NOT NULL DEFAULT TRUE,
    ahr_created_by           VARCHAR(255),
    ahr_created_date         TIMESTAMPTZ NOT NULL DEFAULT now(),
    ahr_updated_by           VARCHAR(255),
    ahr_updated_date         TIMESTAMPTZ,
    ahr_version              VARCHAR(50),
    ahr_uuid                 VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    ahr_ldbid                VARCHAR(64),
    ahr_host_ts              TIMESTAMPTZ,
    ahr_local_ts             TIMESTAMPTZ,
    ahr_rule_system_id       INT,
    ahr_crud_value           VARCHAR(10),
    ahr_workstation_id       INT
);
CREATE INDEX idx_ahr_account ON public.account_holder_roles(ahr_account_id);
CREATE INDEX idx_ahr_user ON public.account_holder_roles(ahr_user_id);
CREATE INDEX idx_ahr_role_type ON public.account_holder_roles(ahr_role_type_id);

CREATE TABLE public.fd_premature_closure (
    fpc_closure_id               SERIAL PRIMARY KEY,
    fpc_account_id               INT NOT NULL REFERENCES public.fd_accounts(fa_account_id),
    fpc_closure_date             TIMESTAMPTZ NOT NULL DEFAULT now(),
    fpc_penalty_pct_applied      DECIMAL(9,4),
    fpc_penalty_amount           DECIMAL(18,4),
    fpc_net_payable_amount       DECIMAL(18,4),
    fpc_currency_id              INT NOT NULL REFERENCES public.fd_currency_ref(fcr_currency_id),
    fpc_reason                   VARCHAR(500),
    fpc_applied_rule_outcome_id  INT REFERENCES public.fd_rule_outcome_ref(fror_outcome_id),
    fpc_approved_by              INT REFERENCES public.fd_user_ref(fur_user_id),
    fpc_created_by               VARCHAR(255),
    fpc_created_date             TIMESTAMPTZ NOT NULL DEFAULT now(),
    fpc_updated_by               VARCHAR(255),
    fpc_updated_date             TIMESTAMPTZ,
    fpc_version                  VARCHAR(50),
    fpc_uuid                     VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    fpc_ldbid                    VARCHAR(64),
    fpc_host_ts                  TIMESTAMPTZ,
    fpc_local_ts                 TIMESTAMPTZ,
    fpc_rule_system_id           INT,
    fpc_crud_value                VARCHAR(10),
    fpc_workstation_id            INT
);
CREATE INDEX idx_fpc_account ON public.fd_premature_closure(fpc_account_id);
CREATE INDEX idx_fpc_currency ON public.fd_premature_closure(fpc_currency_id);
CREATE INDEX idx_fpc_applied_rule_outcome ON public.fd_premature_closure(fpc_applied_rule_outcome_id);
CREATE INDEX idx_fpc_approved_by ON public.fd_premature_closure(fpc_approved_by);

CREATE TABLE public.fd_maturity_instructions (
    fmi_instruction_id            SERIAL PRIMARY KEY,
    fmi_account_id                INT NOT NULL REFERENCES public.fd_accounts(fa_account_id),
    fmi_instruction_type          VARCHAR(50) NOT NULL,
    fmi_renewal_tenure_months     INT,
    fmi_target_payout_account_id  INT REFERENCES public.fd_payout_accounts(fpa_payout_account_id),
    fmi_created_by                VARCHAR(255),
    fmi_created_date              TIMESTAMPTZ NOT NULL DEFAULT now(),
    fmi_updated_by                VARCHAR(255),
    fmi_updated_date              TIMESTAMPTZ,
    fmi_version                   VARCHAR(50),
    fmi_uuid                      VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    fmi_ldbid                     VARCHAR(64),
    fmi_host_ts                   TIMESTAMPTZ,
    fmi_local_ts                  TIMESTAMPTZ,
    fmi_rule_system_id            INT,
    fmi_crud_value                VARCHAR(10),
    fmi_workstation_id            INT
);
CREATE INDEX idx_fmi_account ON public.fd_maturity_instructions(fmi_account_id);
CREATE INDEX idx_fmi_target_payout_account ON public.fd_maturity_instructions(fmi_target_payout_account_id);
