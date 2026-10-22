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
        List<Object> queryArguments = new ArrayList<>();
        queryArguments.add(lang);
        queryArguments.addAll(categoryIds);

        return jdbc.query("SELECT p.pro_product_id,p.pro_product_code,COALESCE(t.pt_product_name,en.pt_product_name,p.pro_product_name) FROM product p " +
                        "LEFT JOIN product_translation t ON t.pt_product_id=p.pro_product_id AND t.pt_locale_tag=? " +
                        "LEFT JOIN product_translation en ON en.pt_product_id=p.pro_product_id AND en.pt_locale_tag='en' " +
                        "JOIN product_category pc ON pc.pc_category_id=p.pro_category_id " +
                        "WHERE p.pro_status='ACTIVE' AND (p.pro_effective_from IS NULL OR p.pro_effective_from<=CURRENT_DATE()) AND (p.pro_effective_to IS NULL OR p.pro_effective_to>=CURRENT_DATE()) AND p.pro_category_id IN (" + categoryPlaceholders + ") ORDER BY p.pro_product_id",
                (productRows, rowNumber) -> {
                    long productId = productRows.getLong(1);
                    List<CurrencyOffer> currencies = jdbc.query("SELECT c.cur_iso_code,c.cur_decimal_places,pc.pcc_minimum_amount,pc.pcc_maximum_amount " +
                                    "FROM product_currency_config pc JOIN currency c ON c.cur_currency_id=pc.pcc_currency_id " +
                                    "WHERE pc.pcc_product_id=? AND c.cur_is_active=1 ORDER BY c.cur_iso_code",
                            (currencyRows, currencyNumber) -> {
                                String code = currencyRows.getString(1);
                                List<RateTier> tiers = jdbc.query("SELECT rc.rc_value_from,rc.rc_value_to,o.ro_outcome_value,o.ro_compounding_basis,o.ro_payout_frequency " +
                                                "FROM product_rule r JOIN rule_condition rc ON rc.rc_rule_id=r.pr_rule_id " +
                                                "JOIN rule_outcome o ON o.ro_rule_id=r.pr_rule_id " +
                                                "WHERE r.pr_product_id=? AND r.pr_is_active=1 " +
                                                "AND rc.rc_condition_attribute='TENURE_MONTHS' AND o.ro_outcome_type='INTEREST_RATE' " +
                                                "AND o.ro_currency_id=(SELECT cur_currency_id FROM currency WHERE UPPER(cur_iso_code)=UPPER(?)) " +
                                                "ORDER BY CAST(rc.rc_value_from AS UNSIGNED)",
                                        (rateRows, rateNumber) -> new RateTier(
                                                Integer.parseInt(rateRows.getString(1)), Integer.parseInt(rateRows.getString(2)),
                                                new BigDecimal(rateRows.getString(3)), rateRows.getString(4), rateRows.getString(5)),
                                        productId, code);
                                if (tiers.isEmpty()) return null;
                                int minMonths = tiers.stream().mapToInt(RateTier::minimumTenureMonths).min().orElse(1);
                                int maxMonths = tiers.stream().mapToInt(RateTier::maximumTenureMonths).max().orElse(minMonths);
                                return new CurrencyOffer(code, currencyRows.getInt(2), currencyRows.getBigDecimal(3),
                                        currencyRows.getBigDecimal(4), minMonths, maxMonths, tiers);
                            }, productId).stream().filter(java.util.Objects::nonNull).toList();
                    return currencies.isEmpty() ? null : new EligibleOffer(productId, productRows.getString(2),
                            productRows.getString(3), currencies, "ACTUAL_365");
                }, queryArguments.toArray()).stream().filter(Objects::nonNull).toList();
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
}
