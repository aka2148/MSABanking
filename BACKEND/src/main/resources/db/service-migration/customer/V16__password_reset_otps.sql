CREATE TABLE password_reset_otps (
    pro_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    pro_user_id BIGINT NOT NULL,
    pro_code_hmac CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pro_attempts INT NOT NULL DEFAULT 0,
    pro_expires_at DATETIME(6) NOT NULL,
    pro_sent_at DATETIME(6) NOT NULL,
    pro_consumed_at DATETIME(6) NULL,
    pro_created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_password_reset_otp_user FOREIGN KEY (pro_user_id) REFERENCES users(usr_user_id),
    INDEX idx_password_reset_otp_user_sent (pro_user_id, pro_sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
