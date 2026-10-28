package com.bank.productservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Customer view of published Product Builder configurations. No draft or incomplete configuration is exposed. */
@RestController
@RequestMapping("/api/v1/customer-products")
public class CustomerOfferController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public CustomerOfferController(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping("/{family}")
    public List<Offer> list(@PathVariable String family, Authentication actor) {
        String required = family(family);
        List<Long> ids = jdbc.queryForList("SELECT pro_product_id FROM product WHERE pro_status='ACTIVE' " +
                "AND (pro_effective_from IS NULL OR pro_effective_from<=CURRENT_DATE()) " +
                "AND (pro_effective_to IS NULL OR pro_effective_to>=CURRENT_DATE()) ORDER BY pro_product_id", Long.class);
        List<Offer> offers = new ArrayList<>();
        for (Long id : ids) {
            Offer offer = offer(id, required, actor);
            if (offer != null) offers.add(offer);
        }
        return offers;
    }

    @GetMapping("/{family}/{id}")
    public Offer get(@PathVariable String family, @PathVariable long id, Authentication actor) {
        Offer offer = offer(id, family(family), actor);
        if (offer == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Published product is unavailable");
        return offer;
    }

    private Offer offer(long id, String required, Authentication actor) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT pro_product_id,pro_product_code,pro_product_name,pro_description," +
                "pro_category_id,pro_base_currency_code FROM product WHERE pro_product_id=? AND pro_status='ACTIVE' " +
                "AND (pro_effective_from IS NULL OR pro_effective_from<=CURRENT_DATE()) " +
                "AND (pro_effective_to IS NULL OR pro_effective_to>=CURRENT_DATE())", id);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        if (!required.equals(categoryFamily(((Number) row.get("pro_category_id")).longValue()))) return null;
        String snapshot = jdbc.query("SELECT pcr_snapshot_json FROM product_configuration_revision WHERE pcr_product_id=? " +
                "AND pcr_status='ACTIVE' ORDER BY pcr_revision_no DESC LIMIT 1", rs -> rs.next() ? rs.getString(1) : null, id);
        if (snapshot == null) return null;
        try {
            JsonNode config = json.readTree(snapshot);
            if (!config.path("eligibilityRules").isEmpty()) return null; // No verified customer facts are synchronized here.
            if (!config.path("charges").isEmpty()) return null; // Request workflows do not execute configured charges.
            if (config.path("currencies").isEmpty() || config.path("pricingBands").isEmpty()) return null;
            String base = config.path("baseCurrencyCode").asText();
            if (base.isBlank() || !base.equalsIgnoreCase((String) row.get("pro_base_currency_code"))) return null;
            List<Currency> currencies = new ArrayList<>();
            for (JsonNode currency : config.path("currencies")) {
                String code = currency.path("currencyCode").asText();
                if (code.isBlank() || currency.path("minimumAmount").isMissingNode() || currency.path("maximumAmount").isMissingNode()) return null;
                Integer active = jdbc.query("SELECT cur_decimal_places FROM currency WHERE cur_iso_code=? AND cur_is_active=1", rs -> rs.next() ? rs.getInt(1) : null, code);
                if (active == null) return null;
                Integer configured = jdbc.queryForObject("SELECT COUNT(*) FROM product_currency_config p JOIN currency c ON c.cur_currency_id=p.pcc_currency_id WHERE p.pcc_product_id=? AND c.cur_iso_code=?", Integer.class, id, code);
                if (configured == null || configured != 1) return null;
                currencies.add(new Currency(code, currency.path("minimumAmount").decimalValue(), currency.path("maximumAmount").decimalValue(), active));
            }
            List<Band> bands = new ArrayList<>();
            for (JsonNode band : config.path("pricingBands")) {
                if (band.path("currencyCode").asText().isBlank() || band.path("rate").isMissingNode()) return null;
                bands.add(new Band(band.path("currencyCode").asText(), band.path("valueFrom").decimalValue(),
                        band.path("valueTo").decimalValue(), band.path("rate").decimalValue()));
            }
            BigDecimal minimumBalance = BigDecimal.ZERO;
            if ("SAVINGS".equals(required) || "CURRENT".equals(required)) {
                for (JsonNode attribute : config.path("attributes")) {
                    if (!attribute.hasNonNull("definitionId")) continue;
                    String key = jdbc.query("SELECT ad_attribute_key FROM attribute_definitions WHERE ad_attribute_def_id=?",
                            rs -> rs.next() ? rs.getString(1) : null, attribute.path("definitionId").asInt());
                    if ("MINIMUM_BALANCE".equals(key) && attribute.hasNonNull("value")) {
                        minimumBalance = new BigDecimal(attribute.path("value").asText());
                    }
                }
            }
            if (bands.isEmpty() || currencies.stream().anyMatch(c ->
                    !("FOREX".equals(required) && c.code().equals(base))
                            && bands.stream().noneMatch(b -> b.currencyCode().equals(c.code())))) return null;
            return new Offer(((Number) row.get("pro_product_id")).longValue(), (String) row.get("pro_product_code"),
                    (String) row.get("pro_product_name"), (String) row.get("pro_description"), required, base,
                    currencies, bands, minimumBalance,
                    ("SAVINGS".equals(required) || "CURRENT".equals(required)) ? bands : List.of(),
                    "FOREX".equals(required) ? "Own-account conversion using a static demo reference rate" : "Admin review required");
        } catch (Exception ex) {
            return null;
        }
    }

    private String categoryFamily(long id) {
        while (id > 0) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT pc_category_code,pc_parent_category_id FROM product_category WHERE pc_category_id=?", id);
            if (rows.isEmpty()) return "";
            String code = (String) rows.get(0).get("pc_category_code");
            if (List.of("LOAN", "FOREX", "SAVINGS", "CURRENT").contains(code)) return code;
            Object parent = rows.get(0).get("pc_parent_category_id");
            id = parent == null ? 0 : ((Number) parent).longValue();
        }
        return "";
    }

    private String family(String value) {
        String family = value.toUpperCase(java.util.Locale.ROOT);
        if (!List.of("LOAN", "FOREX", "SAVINGS", "CURRENT").contains(family)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return family;
    }

    public record Currency(String code, BigDecimal minimumAmount, BigDecimal maximumAmount, int decimalPlaces) {}
    public record Band(String currencyCode, BigDecimal valueFrom, BigDecimal valueTo, BigDecimal rate) {}
    public record Offer(long productId, String productCode, String productName, String description, String family,
                        String baseCurrencyCode, List<Currency> currencies, List<Band> pricingBands,
                        BigDecimal minimumBalance, List<Band> interestBands, String notice) {}
}
