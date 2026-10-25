package com.bank.fdservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class FdFlowIntegrationTest {

    private static final String JWT_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("moneypal_fd")
            .withUsername("fd_flow_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("app.jwt.secret", () -> JWT_SECRET);
        properties.add("app.kafka.enabled", () -> false);
        properties.add("app.demo.opening-balance.enabled", () -> true);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("End-to-End Flow: Product Catalog Snapshot -> Calculator Estimation -> FD Booking -> FD Funding & Consistent Journals")
    void completeProductToCalculatorBookingAndFundingFlow() throws Exception {
        var mapper = new ObjectMapper();
        long customerId = 888L;
        String customerToken = token(customerId, "ROLE_CUSTOMER");

        // 1. Seed Customer Reference (KYC verified, low risk)
        jdbc.update("INSERT INTO fd_user_ref(fur_user_id,fur_user_uuid,fur_full_name,fur_user_status,fur_kyc_status,fur_risk_category) " +
                        "VALUES(?,?,'Integration Flow Customer','ACTIVE','VERIFIED','LOW')",
                customerId, UUID.randomUUID().toString());

        // 2. Simulate ReferenceSnapshotConsumer consuming product.catalog snapshot for FD_STANDARD
        long catalogProductId = 1L;
        long currencyId = 101L;
        Map<String, Object> product = Map.of(
                "pro_product_code", "FD_STANDARD",
                "pro_product_name", "Standard Fixed Deposit",
                "pro_description", "Approved multi-currency standard fixed deposit offer",
                "pro_base_currency_code", "INR",
                "pro_status", "ACTIVE",
                "pro_event_version", 1
        );
        Map<String, Object> inrCurrency = Map.of(
                "cur_currency_id", currencyId,
                "cur_iso_code", "INR",
                "cur_currency_name", "Indian Rupee",
                "cur_decimal_places", 2,
                "pcc_minimum_amount", new BigDecimal("1000.00"),
                "pcc_maximum_amount", new BigDecimal("10000000.00"),
                "pcc_is_base_currency", true,
                "premature_penalty_points", "1.0000"
        );
        Map<String, Object> inrBand = Map.of(
                "cur_iso_code", "INR",
                "ro_outcome_value", "0.0650",
                "ro_compounding_basis", "QUARTERLY",
                "ro_payout_frequency", "AT_MATURITY",
                "minimumMonth", 12,
                "maximumMonth", 36
        );
        Map<String, Object> catalogPayload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "PRODUCT_CATALOG",
                "schemaVersion", 1,
                "occurredAt", java.time.Instant.now().toString(),
                "aggregateId", Long.toString(catalogProductId),
                "sourceVersion", 1L,
                "product", product,
                "currencies", List.of(inrCurrency),
                "rateBands", List.of(inrBand)
        );

        ReferenceSnapshotConsumer consumer = new ReferenceSnapshotConsumer(jdbc, mapper);
        consumer.productSnapshot(mapper.writeValueAsString(catalogPayload), () -> {});

        // Confirm reference snapshots reach FD tables
        assertEquals("FD_STANDARD", jdbc.queryForObject("SELECT fpr_product_code FROM fd_product_ref WHERE fpr_product_id=?", String.class, catalogProductId));
        assertEquals("INR", jdbc.queryForObject("SELECT fcr_iso_code FROM fd_currency_ref WHERE fcr_currency_id=?", String.class, currencyId));
        assertEquals(new BigDecimal("1.0000"), jdbc.queryForObject("SELECT premature_penalty_points FROM fd_product_currency_ref WHERE product_id=? AND currency_id=?", BigDecimal.class, catalogProductId, currencyId));
        assertEquals(new BigDecimal("0.0650"), jdbc.queryForObject("SELECT annual_rate FROM fd_product_rate_ref WHERE product_id=? AND currency_id=? AND minimum_tenure_months=12", BigDecimal.class, catalogProductId, currencyId));

        // Create savings account with INR 50,000 balance
        jdbc.update("INSERT INTO savings_accounts(sa_account_number,sa_holder_id,sa_currency_id,sa_display_name,sa_balance,sa_status,sa_created_date) " +
                "VALUES('SAV-INR-888',?,?,'Customer INR Savings',50000.00,'ACTIVE',UTC_TIMESTAMP(6))", customerId, currencyId);
        long savingsId = jdbc.queryForObject("SELECT sa_account_id FROM savings_accounts WHERE sa_account_number='SAV-INR-888'", Long.class);

        // 3. Calculator Estimation
        String calcRequest = """
                {
                    "productId": 1,
                    "currencyCode": "INR",
                    "principal": 10000.00,
                    "termDays": 365,
                    "compoundingFrequency": "QUARTERLY"
                }
                """;
        var calcResult = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calculator/estimate")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(calcRequest))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();

        var calcJson = mapper.readTree(calcResult.getResponse().getContentAsString());
        BigDecimal calculatedMaturity = new BigDecimal(calcJson.get("maturityAmount").asText());
        assertEquals(0, new BigDecimal("6.50").compareTo(new BigDecimal(calcJson.get("effectiveInterestRate").asText())));

        // 4. Book FD
        String bookRequest = String.format("""
                {
                    "productId": 1,
                    "savingsAccountId": %d,
                    "principal": 10000.00,
                    "currencyCode": "INR",
                    "tenureMonths": 12
                }
                """, savingsId);

        var bookResult = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/fixed-deposits")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "book-key-flow-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookRequest))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andReturn();

        var bookJson = mapper.readTree(bookResult.getResponse().getContentAsString());
        long fdId = bookJson.get("account_id").asLong();
        assertEquals("PENDING", bookJson.get("status").asText());
        BigDecimal bookedMaturity = new BigDecimal(bookJson.get("maturity_amount").asText());

        // Parity: Calculator maturityAmount matches booked FD maturity_amount down to currency scale
        assertEquals(0, calculatedMaturity.compareTo(bookedMaturity));

        // Confirm exact frozen terms snapshot
        String snapshotStr = jdbc.queryForObject("SELECT fa_terms_snapshot FROM fd_accounts WHERE fa_account_id=?", String.class, fdId);
        var snapshot = mapper.readTree(snapshotStr);
        assertNotNull(snapshot);
        assertEquals(1, snapshot.get("productId").asLong());
        assertEquals(0, new BigDecimal("0.0650").compareTo(new BigDecimal(snapshot.get("annualRate").asText())));
        assertEquals(12, snapshot.get("tenureMonths").asInt());
        assertEquals("QUARTERLY", snapshot.get("compoundingBasis").asText());
        assertEquals("AT_MATURITY", snapshot.get("payoutFrequency").asText());
        assertEquals(0, new BigDecimal("1.0000").compareTo(new BigDecimal(snapshot.get("prematurePenaltyPoints").asText())));
        assertEquals("INR", snapshot.get("currencyCode").asText());

        // 5. Fund FD: Transitions PENDING -> ACTIVE
        var fundResult = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/fixed-deposits/{id}/fund", fdId)
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "fund-key-flow-1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();

        var fundJson = mapper.readTree(fundResult.getResponse().getContentAsString());
        assertEquals("ACTIVE", fundJson.get("status").asText());
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT fa_status FROM fd_accounts WHERE fa_account_id=?", String.class, fdId));

        // 6. Savings account debited: 50,000.00 - 10,000.00 = 40,000.00
        BigDecimal savingsBalance = jdbc.queryForObject("SELECT sa_balance FROM savings_accounts WHERE sa_account_id=?", BigDecimal.class, savingsId);
        assertEquals(0, new BigDecimal("40000.00000000").compareTo(savingsBalance));

        // 7. Savings transaction consistency: DEBIT 10,000.00
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM savings_transactions WHERE st_account_id=? AND st_entry_type='DEBIT' AND st_remarks=?",
                Integer.class, savingsId, "FD funding #" + fdId));

        // 8. FD transaction consistency: FD_FUNDING 10,000.00
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fd_transactions WHERE ft_account_id=? AND ft_source_savings_account_id=?",
                Integer.class, fdId, savingsId));

        // 9. Financial journal consistency: FUNDING:<id> with balanced debit/credit lines
        Map<String, Object> journal = jdbc.queryForMap(
                "SELECT fj_operation, fj_total_debit, fj_total_credit, fj_savings_balance_after FROM fd_journals WHERE fj_key=?", "FUNDING:" + fdId);
        assertEquals("FUNDING", journal.get("fj_operation"));
        assertEquals(0, new BigDecimal("10000.00000000").compareTo((BigDecimal) journal.get("fj_total_debit")));
        assertEquals(0, new BigDecimal("10000.00000000").compareTo((BigDecimal) journal.get("fj_total_credit")));
        assertEquals(0, new BigDecimal("40000.00000000").compareTo((BigDecimal) journal.get("fj_savings_balance_after")));

        List<Map<String, Object>> lines = jdbc.queryForList(
                "SELECT jl_sequence, jl_account_code, jl_debit, jl_credit FROM fd_journal_lines WHERE jl_journal_id=(SELECT fj_id FROM fd_journals WHERE fj_key=?) ORDER BY jl_sequence", "FUNDING:" + fdId);
        assertEquals(2, lines.size());
        assertEquals("SAVINGS", lines.get(0).get("jl_account_code"));
        assertEquals(0, new BigDecimal("10000.00000000").compareTo((BigDecimal) lines.get(0).get("jl_debit")));
        assertEquals("FIXED_DEPOSIT", lines.get(1).get("jl_account_code"));
        assertEquals(0, new BigDecimal("10000.00000000").compareTo((BigDecimal) lines.get(1).get("jl_credit")));

        // 10. Idempotent re-funding returns existing active FD without duplicate debits or journal entries
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/fixed-deposits/{id}/fund", fdId)
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "fund-key-flow-1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_key=?", Integer.class, "FUNDING:" + fdId));
        assertEquals(0, new BigDecimal("40000.00000000").compareTo(jdbc.queryForObject("SELECT sa_balance FROM savings_accounts WHERE sa_account_id=?", BigDecimal.class, savingsId)));
    }

    private String token(long userId, String role) {
        return Jwts.builder().setSubject(Long.toString(userId)).claim("userId", userId).claim("roles", role)
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET)), SignatureAlgorithm.HS512).compact();
    }
}
