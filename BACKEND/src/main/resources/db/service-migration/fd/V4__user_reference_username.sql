ALTER TABLE fd_user_ref
    ADD COLUMN fur_username VARCHAR(255) NULL,
    ADD UNIQUE KEY uq_fd_user_ref_username (fur_username);
