package com.bank.productservice;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class FixedDepositCategories {
    private final JdbcTemplate jdbc;

    public FixedDepositCategories(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Set<Integer> ids() {
        List<CategoryNode> categories = jdbc.query(
                "SELECT pc_category_id,pc_category_code,pc_parent_category_id FROM product_category",
                (rows, rowNumber) -> new CategoryNode(rows.getInt(1), rows.getString(2),
                        rows.getObject(3) == null ? null : rows.getInt(3)));
        Map<Integer, CategoryNode> byId = new HashMap<>();
        categories.forEach(category -> byId.put(category.id(), category));

        Set<Integer> result = new HashSet<>();
        for (CategoryNode category : categories) {
            CategoryNode current = category;
            Set<Integer> visited = new HashSet<>();
            while (current != null && visited.add(current.id())) {
                String code = current.code() == null ? "" : current.code().trim().toUpperCase(Locale.ROOT);
                if ("FIXED_DEPOSIT".equals(code) || "FD".equals(code) || code.contains("FD")) {
                    result.add(category.id());
                    break;
                }
                current = current.parentId() == null ? null : byId.get(current.parentId());
            }
        }
        return result;
    }

    private record CategoryNode(int id, String code, Integer parentId) { }
}
