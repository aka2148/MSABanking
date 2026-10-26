CREATE TABLE customer_email_registry (
    cer_normalized_email VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs NOT NULL,
    cer_created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_customer_email_registry PRIMARY KEY (cer_normalized_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

-- INSERT IGNORE preserves historical rows if older data contains case-variant duplicates.
-- New registrations claim the same normalized key inside their account-creation transaction.
INSERT IGNORE INTO customer_email_registry (cer_normalized_email)
SELECT DISTINCT LOWER(TRIM(cem_email_address))
FROM customer_emails
WHERE TRIM(cem_email_address) <> '';
