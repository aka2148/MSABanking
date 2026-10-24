package com.bank.productservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.util.*;

@RestController
public class EligibleFdOfferController {
    private final JdbcTemplate jdbc;
    private final FixedDepositCategories fdCategories;

    public EligibleFdOfferController(JdbcTemplate jdbc, FixedDepositCategories fdCategories) {
        this.jdbc = jdbc;
        this.fdCategories = fdCategories;
    }

    @GetMapping("/api/v1/fd-products/eligible")
    public List<EligibleOffer> eligibleOffers(@RequestParam(defaultValue = "en") String lang) {
        List<Integer> categoryIds = fdCategories.ids().stream().sorted().toList();
        if (categoryIds.isEmpty()) return List.of();

        String categoryPlaceholders = String.join(",", Collections.nCopies(categoryIds.size(), "?"));
        List<Object> productArguments = new ArrayList<>();
        productArguments.add(lang);
        productArguments.addAll(categoryIds);
        List<ProductRow> products = jdbc.query("SELECT p.pro_product_id,p.pro_product_code,COALESCE(t.pt_product_name,en.pt_product_name,p.pro_product_name) FROM product p " +
                        "LEFT JOIN product_translation t ON t.pt_product_id=p.pro_product_id AND t.pt_locale_tag=? " +
                        "LEFT JOIN product_translation en ON en.pt_product_id=p.pro_product_id AND en.pt_locale_tag='en' " +
                        "JOIN product_category pc ON pc.pc_category_id=p.pro_category_id " +
                        "WHERE p.pro_status='ACTIVE' AND (p.pro_effective_from IS NULL OR p.pro_effective_from<=CURRENT_DATE()) AND (p.pro_effective_to IS NULL OR p.pro_effective_to>=CURRENT_DATE()) AND p.pro_category_id IN (" + categoryPlaceholders + ") ORDER BY p.pro_product_id",
                (rows, rowNumber) -> new ProductRow(rows.getLong(1), rows.getString(2), rows.getString(3)), productArguments.toArray());
        if (products.isEmpty()) return List.of();

        String productPlaceholders = String.join(",", Collections.nCopies(products.size(), "?"));
        Object[] ids = products.stream().map(ProductRow::id).toArray();
        Map<ProductCurrencyKey, List<RateTier>> tiersByCurrency = new HashMap<>();
        jdbc.query("SELECT r.pr_product_id,c.cur_iso_code,rc.rc_value_from,rc.rc_value_to,o.ro_outcome_value,o.ro_compounding_basis,o.ro_payout_frequency " +
                        "FROM product_rule r JOIN rule_condition rc ON rc.rc_rule_id=r.pr_rule_id JOIN rule_outcome o ON o.ro_rule_id=r.pr_rule_id " +
                        "JOIN currency c ON c.cur_currency_id=o.ro_currency_id WHERE r.pr_product_id IN (" + productPlaceholders + ") AND r.pr_is_active=1 " +
                        "AND rc.rc_condition_attribute='TENURE_MONTHS' AND o.ro_outcome_type='INTEREST_RATE' ORDER BY r.pr_product_id,c.cur_iso_code,CAST(rc.rc_value_from AS UNSIGNED)",
                rows -> {
                    while (rows.next()) {
                        ProductCurrencyKey key = new ProductCurrencyKey(rows.getLong(1), rows.getString(2));
                        tiersByCurrency.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new RateTier(
                                Integer.parseInt(rows.getString(3)), Integer.parseInt(rows.getString(4)),
                                new BigDecimal(rows.getString(5)), rows.getString(6), rows.getString(7)));
                    }
                    return null;
                }, ids);

        Map<Long, List<CurrencyOffer>> currenciesByProduct = new HashMap<>();
        jdbc.query("SELECT pc.pcc_product_id,c.cur_iso_code,c.cur_decimal_places,pc.pcc_minimum_amount,pc.pcc_maximum_amount " +
                        "FROM product_currency_config pc JOIN currency c ON c.cur_currency_id=pc.pcc_currency_id " +
                        "WHERE pc.pcc_product_id IN (" + productPlaceholders + ") AND c.cur_is_active=1 ORDER BY pc.pcc_product_id,c.cur_iso_code",
                rows -> {
                    while (rows.next()) {
                        long productId = rows.getLong(1);
                        String code = rows.getString(2);
                        List<RateTier> tiers = tiersByCurrency.getOrDefault(new ProductCurrencyKey(productId, code), List.of());
                        if (tiers.isEmpty()) continue;
                        int minMonths = tiers.stream().mapToInt(RateTier::minimumTenureMonths).min().orElse(1);
                        int maxMonths = tiers.stream().mapToInt(RateTier::maximumTenureMonths).max().orElse(minMonths);
                        currenciesByProduct.computeIfAbsent(productId, ignored -> new ArrayList<>()).add(new CurrencyOffer(
                                code, rows.getInt(3), rows.getBigDecimal(4), rows.getBigDecimal(5), minMonths, maxMonths, tiers));
                    }
                    return null;
                }, ids);

        return products.stream().map(product -> {
            List<CurrencyOffer> currencies = currenciesByProduct.getOrDefault(product.id(), List.of());
            return currencies.isEmpty() ? null : new EligibleOffer(product.id(), product.code(), product.name(), currencies, "ACTUAL_365");
        }).filter(Objects::nonNull).toList();
    }

    public record EligibleOffer(@JsonProperty("product_id") long productId,
                                @JsonProperty("product_code") String productCode,
                                @JsonProperty("product_name") String productName,
                                @JsonProperty("currency_offers") List<CurrencyOffer> currencyOffers,
                                @JsonProperty("maturity_estimate_basis") String maturityEstimateBasis) { }

    public record CurrencyOffer(@JsonProperty("currency_code") String currencyCode,
                                @JsonProperty("decimal_places") int decimalPlaces,
                                @JsonProperty("minimum_amount") BigDecimal minimumAmount,
                                @JsonProperty("maximum_amount") BigDecimal maximumAmount,
                                @JsonProperty("minimum_tenure_months") int minimumTenureMonths,
                                @JsonProperty("maximum_tenure_months") int maximumTenureMonths,
                                @JsonProperty("rate_tiers") List<RateTier> rateTiers) { }

    public record RateTier(@JsonProperty("minimum_tenure_months") int minimumTenureMonths,
                           @JsonProperty("maximum_tenure_months") int maximumTenureMonths,
                           @JsonProperty("annual_rate") BigDecimal annualRate,
                           @JsonProperty("compounding_frequency") String compoundingFrequency,
                           @JsonProperty("payout_frequency") String payoutFrequency) { }

    private record ProductRow(long id, String code, String name) { }
    private record ProductCurrencyKey(long productId, String currencyCode) { }
}
