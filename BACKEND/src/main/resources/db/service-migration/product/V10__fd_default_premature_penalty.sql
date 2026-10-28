-- Standard FD premature-closure reduction: 1.00 percentage point.
-- Preserve every active, explicitly configured per-currency penalty.
INSERT INTO product_rule(pr_product_id,pr_rule_type_id,pr_rule_name,pr_priority,pr_combine_logic,pr_is_active,pr_created_by)
WITH RECURSIVE fd_categories(category_id,category_path) AS (
    SELECT pc_category_id,CAST(CONCAT(',',pc_category_id,',') AS CHAR(4000))
    FROM product_category
    WHERE UPPER(TRIM(pc_category_code)) IN ('FD','FIXED_DEPOSIT')
       OR UPPER(TRIM(pc_category_code)) LIKE '%FD%'
    UNION ALL
    SELECT child.pc_category_id,CONCAT(parent.category_path,child.pc_category_id,',')
    FROM product_category child
    JOIN fd_categories parent ON child.pc_parent_category_id=parent.category_id
    WHERE LOCATE(CONCAT(',',child.pc_category_id,','),parent.category_path)=0
)
SELECT DISTINCT p.pro_product_id,
       (SELECT rut_rule_type_id FROM rule_type WHERE rut_rule_type_code='FD_TENURE_RATE'),
       CONCAT('CLOSURE_PENALTY_DEFAULT_',currency.cur_iso_code),
       (SELECT COALESCE(MAX(existing.pr_priority),0)+1
        FROM product_rule existing WHERE existing.pr_product_id=p.pro_product_id),
       'AND',1,'FLYWAY_V10'
FROM product p
JOIN fd_categories category ON category.category_id=p.pro_category_id
JOIN product_currency_config currency_config ON currency_config.pcc_product_id=p.pro_product_id
JOIN currency ON currency.cur_currency_id=currency_config.pcc_currency_id
WHERE NOT EXISTS (
    SELECT 1
    FROM product_rule existing
    JOIN rule_outcome outcome ON outcome.ro_rule_id=existing.pr_rule_id
    WHERE existing.pr_product_id=p.pro_product_id
      AND existing.pr_is_active=1
      AND outcome.ro_currency_id=currency_config.pcc_currency_id
      AND outcome.ro_outcome_type='CLOSURE_PENALTY'
);

INSERT INTO rule_outcome(ro_rule_id,ro_outcome_type,ro_outcome_value,ro_unit,ro_currency_id,ro_created_by)
SELECT penalty_rule.pr_rule_id,'CLOSURE_PENALTY',1.0000,'PERCENTAGE_POINTS',
       currency_config.pcc_currency_id,'FLYWAY_V10'
FROM product_rule penalty_rule
JOIN product_currency_config currency_config
  ON currency_config.pcc_product_id=penalty_rule.pr_product_id
JOIN currency ON currency.cur_currency_id=currency_config.pcc_currency_id
WHERE penalty_rule.pr_created_by='FLYWAY_V10'
  AND penalty_rule.pr_rule_name=CONCAT('CLOSURE_PENALTY_DEFAULT_',currency.cur_iso_code)
  AND NOT EXISTS (
      SELECT 1
      FROM rule_outcome outcome
      JOIN product_rule active_rule ON active_rule.pr_rule_id=outcome.ro_rule_id
      WHERE active_rule.pr_product_id=penalty_rule.pr_product_id
        AND active_rule.pr_is_active=1
        AND outcome.ro_currency_id=currency_config.pcc_currency_id
        AND outcome.ro_outcome_type='CLOSURE_PENALTY'
  );
