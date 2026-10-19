CREATE TABLE fd_demo_clock (
    fdc_clock_id TINYINT NOT NULL PRIMARY KEY,
    fdc_offset_days INT NOT NULL DEFAULT 0,
    fdc_updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_fd_demo_clock_singleton CHECK (fdc_clock_id = 1),
    CONSTRAINT ck_fd_demo_clock_offset CHECK (fdc_offset_days BETWEEN 0 AND 3650)
) ENGINE=InnoDB;

INSERT INTO fd_demo_clock(fdc_clock_id, fdc_offset_days) VALUES(1, 0);
