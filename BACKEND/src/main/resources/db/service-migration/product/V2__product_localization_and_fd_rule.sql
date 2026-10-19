CREATE TABLE `product_translation` (
    `pt_translation_id` BIGINT NOT NULL AUTO_INCREMENT,
    `pt_product_id` BIGINT NOT NULL,
    `pt_locale_tag` VARCHAR(35) NOT NULL,
    `pt_product_name` VARCHAR(255) NOT NULL,
    `pt_description` VARCHAR(1000),
    `pt_created_date` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `pt_updated_date` DATETIME(6),
    PRIMARY KEY (`pt_translation_id`),
    UNIQUE KEY `uq_product_translation_locale` (`pt_product_id`,`pt_locale_tag`),
    CONSTRAINT `fk_product_translation_product` FOREIGN KEY (`pt_product_id`) REFERENCES `product` (`pro_product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

INSERT INTO `rule_type` (`rut_rule_type_code`,`rut_description`,`rut_created_by`)
SELECT 'FD_TENURE_RATE','Fixed deposit annual interest rate by tenure and amount','SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM `rule_type` WHERE `rut_rule_type_code`='FD_TENURE_RATE');
