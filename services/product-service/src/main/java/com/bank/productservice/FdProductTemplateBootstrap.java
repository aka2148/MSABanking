package com.bank.productservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Repeatable, idempotent startup bootstrap for approved fixed-deposit templates.
 * Reuses existing product, currency, rule, condition, and outcome tables and routes.
 * Ensures the approved multi-currency template FD_STANDARD is seeded and activated once.
 */
@Component
public class FdProductTemplateBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(FdProductTemplateBootstrap.class);
    public static final String STANDARD_PRODUCT_CODE = "FD_STANDARD";

    private final JdbcTemplate jdbc;
    private final FdOfferController fdOfferController;
    private final FixedDepositCategories fdCategories;
    private final boolean enabled;

    public FdProductTemplateBootstrap(
            JdbcTemplate jdbc,
            FdOfferController fdOfferController,
            FixedDepositCategories fdCategories,
            @Value("${app.product.template-bootstrap.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.fdOfferController = fdOfferController;
        this.fdCategories = fdCategories;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("FD product template bootstrap is disabled via configuration.");
            return;
        }

        // 1. Resolve FD Category ID
        org.springframework.security.core.Authentication previousAuth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        try {
            var systemAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    "system-bootstrap", null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(systemAuth);

            List<Long> categoryIds = jdbc.queryForList(
                    "SELECT pc_category_id FROM product_category WHERE UPPER(pc_category_code)='FIXED_DEPOSIT'", Long.class);
            Long categoryId = categoryIds.isEmpty() ? null : categoryIds.get(0);
            if (categoryId == null) {
                var discovered = fdCategories.ids();
                if (!discovered.isEmpty()) {
                    categoryId = discovered.iterator().next().longValue();
                }
            }
            if (categoryId == null) {
                log.warn("No fixed-deposit category found in product_category; skipping template setup.");
                return;
            }

            // 2. Prevent duplicate seeds on repeated startup
            List<Map<String, Object>> existing = jdbc.queryForList(
                    "SELECT pro_product_id, pro_status FROM product WHERE UPPER(pro_product_code)=UPPER(?)", STANDARD_PRODUCT_CODE);
            if (!existing.isEmpty()) {
                long existingId = ((Number) existing.get(0).get("pro_product_id")).longValue();
                String status = (String) existing.get(0).get("pro_status");
                if (!"ACTIVE".equalsIgnoreCase(status)) {
                    log.info("Activating existing {} (ID: {})", STANDARD_PRODUCT_CODE, existingId);
                    fdOfferController.activate(existingId);
                } else {
                    log.info("Approved template {} is already present and ACTIVE; skipping duplicate setup.", STANDARD_PRODUCT_CODE);
                }
                return;
            }

            // 3. Ensure required currencies are present and active
            ensureCurrency("INR", "Indian Rupee", "₹", 2);
            ensureCurrency("USD", "US Dollar", "$", 2);
            ensureCurrency("KWD", "Kuwaiti Dinar", "KD", 3);
            ensureCurrency("JPY", "Japanese Yen", "¥", 0);

            // 4. Construct approved multi-currency standard template
            BigDecimal penaltyPoints = new BigDecimal("1.0000");

            List<FdOfferController.RateBand> inrBands = List.of(
                    new FdOfferController.RateBand(1, 5, new BigDecimal("0.0400"), "QUARTERLY", "AT_MATURITY"),
                    new FdOfferController.RateBand(6, 11, new BigDecimal("0.0550"), "QUARTERLY", "AT_MATURITY"),
                    new FdOfferController.RateBand(12, 36, new BigDecimal("0.0650"), "QUARTERLY", "AT_MATURITY"),
                    new FdOfferController.RateBand(37, 60, new BigDecimal("0.0625"), "QUARTERLY", "AT_MATURITY")
            );
            FdOfferController.CurrencySchedule inrSchedule = new FdOfferController.CurrencySchedule(
                    "INR", new BigDecimal("1000.00"), new BigDecimal("10000000.00"), inrBands, penaltyPoints);

            List<FdOfferController.RateBand> usdBands = List.of(
                    new FdOfferController.RateBand(1, 5, new BigDecimal("0.0300"), "QUARTERLY", "AT_MATURITY"),
                    new FdOfferController.RateBand(6, 11, new BigDecimal("0.0350"), "QUARTERLY", "AT_MATURITY"),
                    new FdOfferController.RateBand(12, 36, new BigDecimal("0.0400"), "QUARTERLY", "AT_MATURITY"),
                    new FdOfferController.RateBand(37, 60, new BigDecimal("0.0375"), "QUARTERLY", "AT_MATURITY")
            );
            FdOfferController.CurrencySchedule usdSchedule = new FdOfferController.CurrencySchedule(
                    "USD", new BigDecimal("100.00"), new BigDecimal("1000000.00"), usdBands, penaltyPoints);

            List<FdOfferController.RateBand> kwdBands = List.of(
                    new FdOfferController.RateBand(1, 5, new BigDecimal("0.0250"), "QUARTERLY", "AT_MATURITY"),
                    new FdOfferController.RateBand(6, 11, new BigDecimal("0.0350"), "QUARTERLY", "AT_MATURITY"),
                    new FdOfferController.RateBand(12, 36, new BigDecimal("0.0400"), "QUARTERLY", "AT_MATURITY"),
                    new FdOfferController.RateBand(37, 60, new BigDecimal("0.0375"), "QUARTERLY", "AT_MATURITY")
            );
            FdOfferController.CurrencySchedule kwdSchedule = new FdOfferController.CurrencySchedule(
                    "KWD", new BigDecimal("10.000"), new BigDecimal("100000.000"), kwdBands, penaltyPoints);

            List<FdOfferController.RateBand> jpyBands = List.of(
                    new FdOfferController.RateBand(1, 5, new BigDecimal("0.0020"), "QUARTERLY", "AT_MATURITY"),
                    new FdOfferController.RateBand(6, 11, new BigDecimal("0.0050"), "QUARTERLY", "AT_MATURITY"),
                    new FdOfferController.RateBand(12, 36, new BigDecimal("0.0075"), "QUARTERLY", "AT_MATURITY"),
                    new FdOfferController.RateBand(37, 60, new BigDecimal("0.0100"), "QUARTERLY", "AT_MATURITY")
            );
            FdOfferController.CurrencySchedule jpySchedule = new FdOfferController.CurrencySchedule(
                    "JPY", new BigDecimal("10000"), new BigDecimal("1000000000"), jpyBands, penaltyPoints);

            FdOfferController.OfferRequest request = new FdOfferController.OfferRequest(
                    STANDARD_PRODUCT_CODE,
                    "Standard Fixed Deposit",
                    categoryId,
                    "Illustrative multi-currency fixed deposit with quarterly compounding and payout at maturity.",
                    "INR",
                    List.of(inrSchedule, usdSchedule, kwdSchedule, jpySchedule),
                    LocalDate.now(),
                    LocalDate.now().plusYears(10)
            );

            // 5. Create and Activate
            FdOfferController.OfferResponse created = fdOfferController.create(request);
            log.info("Created {} offer with ID: {}", STANDARD_PRODUCT_CODE, created.catalogProductId());

            FdOfferController.OfferResponse activated = fdOfferController.activate(created.catalogProductId());
            log.info("Activated {} offer with ID: {}. Status: {}", STANDARD_PRODUCT_CODE, activated.catalogProductId(), activated.status());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(previousAuth);
        }
    }

    private void ensureCurrency(String code, String name, String symbol, int scale) {
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM currency WHERE UPPER(cur_iso_code)=UPPER(?)", Integer.class, code);
        if (count == 0) {
            jdbc.update("INSERT INTO currency(cur_iso_code,cur_currency_name,cur_currency_symbol,cur_decimal_places,cur_is_active,cur_created_by) VALUES(?,?,?,?,1,'BOOTSTRAP')",
                    code, name, symbol, scale);
        }
    }
}
