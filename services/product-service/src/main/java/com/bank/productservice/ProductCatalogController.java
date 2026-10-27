package com.bank.productservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
public class ProductCatalogController {
    private final JdbcTemplate jdbc;
    private final FdComplianceService compliance;

    public ProductCatalogController(JdbcTemplate jdbc, FdComplianceService compliance) {
        this.jdbc = jdbc;
        this.compliance = compliance;
    }

    @GetMapping("/api/product")
    public List<ProductView> activeProducts(@RequestParam(defaultValue = "en") String lang) {
        return products("ACTIVE", lang);
    }

    @GetMapping("/api/product/all")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public List<ProductView> allFdProducts(@RequestParam(defaultValue = "en") String lang) {
        return products(null, lang);
    }

    @GetMapping("/api/v1/products")
    public List<ProductView> list(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "en") String lang) {
        return products(status, lang);
    }

    @GetMapping("/api/v1/products/{id}")
    public ProductView get(@PathVariable long id, @RequestParam(defaultValue = "en") String lang) {
        return product(id, lang);
    }

    @GetMapping("/api/v1/products/by-code/{code}")
    public ProductView byCode(@PathVariable String code, @RequestParam(defaultValue = "en") String lang) {
        Long id = jdbc.query(
                "SELECT pro_product_id FROM product WHERE UPPER(pro_product_code)=UPPER(?) AND pro_status='ACTIVE'",
                rs -> rs.next() ? rs.getLong(1) : null, code);
        if (id == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active product not found");
        return product(id, lang);
    }

    @PostMapping("/api/v1/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    @Transactional
    public ProductView create(@Valid @RequestBody ProductRequest request) {
        ProductTextRules.requireIdentity(request.productName, request.description);
        String code = request.productCode.trim().toUpperCase(Locale.ROOT);
        if (jdbc.queryForList("SELECT pro_product_id FROM product WHERE pro_product_code=?", Long.class, code)
                .size() > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product code already exists");
        requireCategory(request.categoryId);
        if (request.baseCurrencyCode != null)
            requireCurrency(request.baseCurrencyCode, true);
        jdbc.update(
                "INSERT INTO product(pro_product_code,pro_product_name,pro_category_id,pro_base_currency_code,pro_status,pro_effective_from,pro_effective_to,pro_description,pro_created_by) VALUES(?,?,?,?,?,?,?,?,?)",
                code, ProductTextRules.name(request.productName), request.categoryId, request.baseCurrencyCode, "DRAFT",
                request.effectiveFrom, request.effectiveTo,
                request.description == null ? null : request.description.trim(), "API");
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        saveTranslation(id, request.locale, ProductTextRules.name(request.productName),
                request.description == null ? null : request.description.trim());
        return product(id, request.locale == null ? "en" : request.locale);
    }

    @PutMapping("/api/v1/products/{id}")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    @Transactional
    public ProductView update(@PathVariable long id, @Valid @RequestBody ProductRequest request) {
        ProductTextRules.requireIdentity(request.productName, request.description);
        product(id, "en");
        requireCategory(request.categoryId);
        if (request.baseCurrencyCode != null)
            requireCurrency(request.baseCurrencyCode, true);
        jdbc.update(
                "UPDATE product SET pro_product_name=?,pro_category_id=?,pro_base_currency_code=?,pro_effective_from=?,pro_effective_to=?,pro_description=?,pro_updated_by='API',pro_updated_date=UTC_TIMESTAMP(6) WHERE pro_product_id=?",
                ProductTextRules.name(request.productName), request.categoryId, request.baseCurrencyCode,
                request.effectiveFrom, request.effectiveTo,
                request.description == null ? null : request.description.trim(), id);
        saveTranslation(id, request.locale, ProductTextRules.name(request.productName),
                request.description == null ? null : request.description.trim());
        return product(id, request.locale == null ? "en" : request.locale);
    }

    @PatchMapping("/api/v1/products/{id}/status")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    @Transactional
    public ProductView status(@PathVariable long id, @RequestBody Map<String, String> body) {
        ProductView existing = product(id, "en");
        String status = body.get("status");
        if (status == null || !List.of("DRAFT", "ACTIVE", "INACTIVE").contains(status.toUpperCase(Locale.ROOT)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be DRAFT, ACTIVE, or INACTIVE");
        if ("ACTIVE".equalsIgnoreCase(status)) {
            publishSnapshot(id);
            Integer isFd = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM product p JOIN product_category pc ON pc.pc_category_id=p.pro_category_id WHERE p.pro_product_id=? AND (pc.pc_category_name LIKE '%FIXED%DEPOSIT%' OR pc.pc_category_name LIKE '%FD%' OR EXISTS (SELECT 1 FROM product_rule pr WHERE pr.pr_product_id=p.pro_product_id AND pr.pr_rule_name='TENURE_RATE'))",
                    Integer.class, id);
            if (isFd != null && isFd > 0) {
                compliance.validateProductScheduleForActivation(id);
            }
        }
        jdbc.update(
                "UPDATE product SET pro_status=?,pro_event_version=pro_event_version+1,pro_updated_by='API',pro_updated_date=UTC_TIMESTAMP(6) WHERE pro_product_id=?",
                status.toUpperCase(Locale.ROOT), id);
        // Every lifecycle change must reach FD, including deactivation and returning to
        // draft.
        writeOutbox(id);
        return product(existing.productId, "en");
    }

    @DeleteMapping("/api/v1/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable long id) {
        product(id, "en");
        Integer refs = jdbc.queryForObject("SELECT COUNT(*) FROM product_currency_config WHERE pcc_product_id=?",
                Integer.class, id);
        if (refs != null && refs > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Products with currency or FD configuration cannot be deleted; deactivate them instead");
        jdbc.update("DELETE FROM product_translation WHERE pt_product_id=?", id);
        jdbc.update("DELETE FROM product WHERE pro_product_id=?", id);
    }

    @PutMapping("/api/v1/products/{id}/translations/{locale}")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public void translation(@PathVariable long id, @PathVariable String locale,
            @Valid @RequestBody TranslationRequest request) {
        product(id, "en");
        saveTranslation(id, locale, request.productName, request.description);
    }

    private List<ProductView> products(String status, String locale) {
        String sql = "SELECT p.pro_product_id,p.pro_product_code,COALESCE(t.pt_product_name,en.pt_product_name,p.pro_product_name),p.pro_category_id,p.pro_base_currency_code,p.pro_status,p.pro_effective_from,p.pro_effective_to,COALESCE(t.pt_description,en.pt_description,p.pro_description) FROM product p LEFT JOIN product_translation t ON t.pt_product_id=p.pro_product_id AND t.pt_locale_tag=? LEFT JOIN product_translation en ON en.pt_product_id=p.pro_product_id AND en.pt_locale_tag='en'"
                + (status == null ? "" : " WHERE p.pro_status=?") + " ORDER BY p.pro_product_id";
        return status == null ? jdbc.query(sql, (r, n) -> map(r), (locale))
                : jdbc.query(sql, (r, n) -> map(r), locale, status);
    }

    private ProductView product(long id, String locale) {
        List<ProductView> values = jdbc.query(
                "SELECT p.pro_product_id,p.pro_product_code,COALESCE(t.pt_product_name,en.pt_product_name,p.pro_product_name),p.pro_category_id,p.pro_base_currency_code,p.pro_status,p.pro_effective_from,p.pro_effective_to,COALESCE(t.pt_description,en.pt_description,p.pro_description) FROM product p LEFT JOIN product_translation t ON t.pt_product_id=p.pro_product_id AND t.pt_locale_tag=? LEFT JOIN product_translation en ON en.pt_product_id=p.pro_product_id AND en.pt_locale_tag='en' WHERE p.pro_product_id=?",
                (r, n) -> map(r), locale, id);
        if (values.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        return values.get(0);
    }

    private ProductView map(java.sql.ResultSet r) throws java.sql.SQLException {
        return new ProductView(r.getLong(1), r.getString(2), r.getString(3), r.getLong(4), r.getString(5),
                r.getString(6), r.getString(7), r.getString(8), r.getString(9));
    }

    private void saveTranslation(long id, String locale, String name, String description) {
        String language = locale == null || locale.isBlank() ? "en" : locale.trim();
        jdbc.update(
                "INSERT INTO product_translation(pt_product_id,pt_locale_tag,pt_product_name,pt_description) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE pt_product_name=VALUES(pt_product_name),pt_description=VALUES(pt_description),pt_updated_date=UTC_TIMESTAMP(6)",
                id, language, name, description);
    }

    private void requireCategory(long id) {
        if (jdbc.queryForList("SELECT pc_category_id FROM product_category WHERE pc_category_id=?", Long.class, id)
                .isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product category does not exist");
    }

    private CurrencyView requireCurrency(String code, boolean active) {
        List<CurrencyView> rows = jdbc.query(
                "SELECT cur_currency_id,cur_iso_code,cur_currency_name,cur_currency_symbol,cur_decimal_places,cur_is_active FROM currency WHERE UPPER(cur_iso_code)=UPPER(?)",
                (r, n) -> new CurrencyView(r.getLong(1), r.getString(2), r.getString(3), r.getString(4), r.getInt(5),
                        r.getBoolean(6)),
                code);
        if (rows.isEmpty() || (active && !rows.get(0).active))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency is missing or inactive: " + code);
        return rows.get(0);
    }

    private void publishSnapshot(long id) {
        if (jdbc.queryForList("SELECT pcc_config_id FROM product_currency_config WHERE pcc_product_id=?", Long.class,
                id).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Configure at least one currency before activation");
    }

    private void writeOutbox(long id) {
        Map<String, Object> product = jdbc.queryForMap(
                "SELECT pro_product_code,pro_product_name,pro_description,pro_base_currency_code,pro_status,pro_event_version FROM product WHERE pro_product_id=?",
                id);
        List<Map<String, Object>> currency = jdbc.queryForList(
                "SELECT c.cur_currency_id,c.cur_iso_code,c.cur_currency_name,c.cur_decimal_places,p.pcc_minimum_amount,p.pcc_maximum_amount,p.pcc_is_base_currency,(SELECT o.ro_outcome_value FROM product_rule pr JOIN rule_outcome o ON o.ro_rule_id=pr.pr_rule_id WHERE pr.pr_product_id=p.pcc_product_id AND o.ro_currency_id=p.pcc_currency_id AND o.ro_outcome_type='CLOSURE_PENALTY' AND pr.pr_is_active=1 ORDER BY pr.pr_priority LIMIT 1) premature_penalty_points FROM product_currency_config p JOIN currency c ON c.cur_currency_id=p.pcc_currency_id WHERE p.pcc_product_id=? ORDER BY c.cur_iso_code",
                id);
        List<Map<String, Object>> rates = jdbc.queryForList(
                "SELECT o.ro_outcome_value,o.ro_currency_id,c.cur_iso_code,MAX(CASE WHEN q.rc_condition_attribute='TENURE_MONTHS' THEN q.rc_value_from END) min_month,MAX(CASE WHEN q.rc_condition_attribute='TENURE_MONTHS' THEN q.rc_value_to END) max_month FROM product_rule r JOIN rule_outcome o ON o.ro_rule_id=r.pr_rule_id JOIN currency c ON c.cur_currency_id=o.ro_currency_id JOIN rule_condition q ON q.rc_rule_id=r.pr_rule_id WHERE r.pr_product_id=? AND r.pr_is_active=1 AND o.ro_outcome_type='INTEREST_RATE' GROUP BY r.pr_rule_id,o.ro_outcome_value,o.ro_currency_id,c.cur_iso_code ORDER BY c.cur_iso_code,min_month",
                id);
        Map<String, Object> payload = Map.of("eventId", UUID.randomUUID().toString(), "eventType", "PRODUCT_CATALOG",
                "schemaVersion", 1, "occurredAt", java.time.Instant.now().toString(), "aggregateId", Long.toString(id),
                "sourceVersion", product.get("pro_event_version"), "product", product, "currencies", currency,
                "rateBands", rates);
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
            jdbc.update(
                    "INSERT INTO outbox_event(event_key,event_type,topic,source_version,payload) VALUES(?,?,?,?,CAST(? AS JSON))",
                    Long.toString(id), "PRODUCT_CATALOG", "product.catalog", product.get("pro_event_version"), json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize product snapshot", ex);
        }
    }

    public record ProductRequest(@NotBlank @Size(max = 50) String productCode,
            @NotBlank @Size(max = 255) String productName, @NotNull Long categoryId, String baseCurrencyCode,
            String effectiveFrom, String effectiveTo, @Size(max = 1000) String description, String locale) {
    }

    public record TranslationRequest(@NotBlank String productName, @Size(max = 1000) String description) {
    }

    public record ProductView(Long productId, String productCode, String productName, Long categoryId,
            String baseCurrencyCode, String status, String effectiveFrom, String effectiveTo, String description) {
    }

    public record CurrencyView(Long currencyId, String isoCode, String currencyName, String currencySymbol,
            Integer decimalPlaces, boolean active) {
    }
}
