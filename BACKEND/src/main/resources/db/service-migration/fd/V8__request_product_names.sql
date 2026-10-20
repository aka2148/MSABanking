ALTER TABLE loan_applications ADD COLUMN la_product_name VARCHAR(255) NOT NULL DEFAULT 'Loan product';
ALTER TABLE forex_requests ADD COLUMN fr_product_name VARCHAR(255) NOT NULL DEFAULT 'Forex service';
