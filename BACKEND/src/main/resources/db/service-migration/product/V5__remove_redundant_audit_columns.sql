-- Remove the legacy audit columns while preserving created/updated actor and date fields.
DROP PROCEDURE IF EXISTS remove_redundant_audit_columns;
DELIMITER $$
CREATE PROCEDURE remove_redundant_audit_columns()
BEGIN
    DECLARE finished BOOLEAN DEFAULT FALSE;
    DECLARE current_table VARCHAR(64);
    DECLARE current_column VARCHAR(64);
    DECLARE redundant_columns CURSOR FOR
        SELECT TABLE_NAME, COLUMN_NAME
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND COLUMN_NAME REGEXP '_(host_ts|local_ts|rule_system_id|crud_value)$';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = TRUE;

    OPEN redundant_columns;
    drop_columns: LOOP
        FETCH redundant_columns INTO current_table, current_column;
        IF finished THEN
            LEAVE drop_columns;
        END IF;

        SET @drop_column_sql = CONCAT(
            'ALTER TABLE `', REPLACE(current_table, '`', '``'),
            '` DROP COLUMN `', REPLACE(current_column, '`', '``'), '`'
        );
        PREPARE drop_column_statement FROM @drop_column_sql;
        EXECUTE drop_column_statement;
        DEALLOCATE PREPARE drop_column_statement;
    END LOOP;
    CLOSE redundant_columns;
END$$
DELIMITER ;

CALL remove_redundant_audit_columns();
DROP PROCEDURE remove_redundant_audit_columns;
