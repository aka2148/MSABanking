CREATE TABLE customer_mpin (
  cmp_user_id BIGINT NOT NULL PRIMARY KEY,
  cmp_hash VARCHAR(512) NOT NULL,
  cmp_set_at TIMESTAMP(6) NOT NULL,
  cmp_failed_attempts INT NOT NULL DEFAULT 0,
  cmp_locked_until TIMESTAMP(6) NULL,
  cmp_last_failed_at TIMESTAMP(6) NULL,
  cmp_version INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_customer_mpin_user FOREIGN KEY (cmp_user_id) REFERENCES users(usr_user_id)
);

CREATE TABLE customer_mpin_events (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  actor_username VARCHAR(100) NOT NULL,
  purpose VARCHAR(40) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_customer_mpin_events_user (user_id, created_at),
  CONSTRAINT fk_customer_mpin_event_user FOREIGN KEY (user_id) REFERENCES users(usr_user_id)
);
