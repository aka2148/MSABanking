-- Returns any BASE TABLE in the three active schemas missing one or more requested columns.
-- An empty first result set means every discovered base table has all four column names.

SELECT
    t.TABLE_SCHEMA AS schema_name,
    t.TABLE_NAME AS table_name,
    CONCAT_WS(', ',
        IF(h.COLUMN_NAME IS NULL, 'host_timestamp', NULL),
        IF(l.COLUMN_NAME IS NULL, 'local_timestamp', NULL),
        IF(c.COLUMN_NAME IS NULL, 'crud_value', NULL),
        IF(d.COLUMN_NAME IS NULL, 'ldb_id', NULL)
    ) AS missing_columns
FROM information_schema.TABLES AS t
LEFT JOIN information_schema.COLUMNS AS h
    ON h.TABLE_SCHEMA = t.TABLE_SCHEMA
   AND h.TABLE_NAME = t.TABLE_NAME
   AND h.COLUMN_NAME = 'host_timestamp'
LEFT JOIN information_schema.COLUMNS AS l
    ON l.TABLE_SCHEMA = t.TABLE_SCHEMA
   AND l.TABLE_NAME = t.TABLE_NAME
   AND l.COLUMN_NAME = 'local_timestamp'
LEFT JOIN information_schema.COLUMNS AS c
    ON c.TABLE_SCHEMA = t.TABLE_SCHEMA
   AND c.TABLE_NAME = t.TABLE_NAME
   AND c.COLUMN_NAME = 'crud_value'
LEFT JOIN information_schema.COLUMNS AS d
    ON d.TABLE_SCHEMA = t.TABLE_SCHEMA
   AND d.TABLE_NAME = t.TABLE_NAME
   AND d.COLUMN_NAME = 'ldb_id'
WHERE t.TABLE_SCHEMA IN ('moneypal_fd', 'moneypal_customer', 'moneypal_product')
  AND t.TABLE_TYPE = 'BASE TABLE'
  AND (h.COLUMN_NAME IS NULL OR l.COLUMN_NAME IS NULL
       OR c.COLUMN_NAME IS NULL OR d.COLUMN_NAME IS NULL)
ORDER BY t.TABLE_SCHEMA, t.TABLE_NAME;

-- Review column types/defaults and the insert/update trigger coverage separately before
-- enabling application writes against this schema change.
