ALTER TABLE user_credentials
    ADD COLUMN ucr_mfa_secret VARCHAR(512) NULL,
    ADD COLUMN ucr_token_version INT NOT NULL DEFAULT 0;

CREATE TABLE password_reset_tokens (
    prt_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    prt_user_id BIGINT NOT NULL,
    prt_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    prt_expires_at DATETIME(6) NOT NULL,
    prt_used_at DATETIME(6) NULL,
    prt_created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_password_reset_token_hash UNIQUE (prt_token_hash),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (prt_user_id) REFERENCES users(usr_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE INDEX idx_password_reset_user_expiry ON password_reset_tokens(prt_user_id, prt_expires_at);

CREATE TABLE mfa_used_totp_steps (
    mut_user_id BIGINT NOT NULL,
    mut_time_step BIGINT NOT NULL,
    mut_used_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_mfa_used_totp_steps PRIMARY KEY (mut_user_id, mut_time_step),
    CONSTRAINT fk_mfa_used_totp_user FOREIGN KEY (mut_user_id) REFERENCES users(usr_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
