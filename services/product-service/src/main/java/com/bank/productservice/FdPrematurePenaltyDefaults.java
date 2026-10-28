package com.bank.productservice;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Ensures every configured FD currency has the standard early-closure rate reduction. */
public final class FdPrematurePenaltyDefaults {
    public static final BigDecimal DEFAULT_POINTS = new BigDecimal("1.0000");
    private static final String CREATED_BY = "FD_PENALTY_DEFAULT";

    private FdPrematurePenaltyDefaults() { }

    public static void ensureForProduct(JdbcTemplate jdbc, FixedDepositCategories categories, long productId) {
        Integer categoryId = jdbc.query(
                "SELECT pro_category_id FROM product WHERE pro_product_id=?",
                rows -> rows.next() ? rows.getInt(1) : null,
                productId);
        Set<Integer> fdCategoryIds = categories.ids();
        if (categoryId == null || !fdCategoryIds.contains(categoryId)) {
            return;
        }

        List<Long> currencyIds = jdbc.queryForList(
                "SELECT pcc_currency_id FROM product_currency_config WHERE pcc_product_id=? ORDER BY pcc_currency_id",
                Long.class,
                productId);
        if (currencyIds.isEmpty()) {
            return;
        }

        Long ruleTypeId = jdbc.queryForObject(
                "SELECT rut_rule_type_id FROM rule_type WHERE rut_rule_type_code='FD_TENURE_RATE'",
                Long.class);
        for (Long currencyId : currencyIds) {
            Integer existing = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM product_rule r JOIN rule_outcome o ON o.ro_rule_id=r.pr_rule_id "
                            + "WHERE r.pr_product_id=? AND r.pr_is_active=1 "
                            + "AND o.ro_currency_id=? AND o.ro_outcome_type='CLOSURE_PENALTY'",
                    Integer.class,
                    productId,
                    currencyId);
            if (existing != null && existing > 0) {
                continue;
            }

            Integer priority = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(pr_priority),0)+1 FROM product_rule WHERE pr_product_id=?",
                    Integer.class,
                    productId);
            jdbc.update(
                    "INSERT INTO product_rule(pr_product_id,pr_rule_type_id,pr_rule_name,pr_priority,pr_combine_logic,pr_is_active,pr_created_by) "
                            + "VALUES(?,?,?,?,?,1,?)",
                    productId, ruleTypeId, "CLOSURE_PENALTY", priority, "AND", CREATED_BY);
            Long ruleId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            jdbc.update(
                    "INSERT INTO rule_outcome(ro_rule_id,ro_outcome_type,ro_outcome_value,ro_unit,ro_currency_id,ro_created_by) "
                            + "VALUES(?,?,?,?,?,?)",
                    ruleId, "CLOSURE_PENALTY", DEFAULT_POINTS, "PERCENTAGE_POINTS", currencyId, CREATED_BY);
        }
    }
}
