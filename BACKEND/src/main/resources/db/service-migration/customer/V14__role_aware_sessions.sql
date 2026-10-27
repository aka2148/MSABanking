CREATE TABLE user_sessions (
    us_session_id CHAR(36) NOT NULL PRIMARY KEY,
    us_user_id BIGINT NOT NULL,
    us_created_at DATETIME(6) NOT NULL,
    us_last_activity_at DATETIME(6) NOT NULL,
    us_expires_at DATETIME(6) NOT NULL,
    us_idle_timeout_seconds INT NOT NULL,
    us_revoked_at DATETIME(6) NULL,
    INDEX idx_user_sessions_user (us_user_id),
    INDEX idx_user_sessions_expiry (us_expires_at),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (us_user_id) REFERENCES users(usr_user_id)
);
