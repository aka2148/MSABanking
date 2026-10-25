package com.bank.fdservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FdBookingAndCalculatorFinancialTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private Authentication actor;

    private ObjectMapper json;
    private DemoClockService clock;
    private FdCalculatorController calculatorController;
    private FixedDepositController depositController;
    private FdLifecycleController lifecycleController;

    private final long userId = 42L;

    @BeforeEach
    void setUp() {
        json = new ObjectMapper();
        clock = new DemoClockService(jdbc, "Asia/Kolkata");
        calculatorController = new FdCalculatorController(jdbc);
        depositController = new FixedDepositController(jdbc, json, clock);
        lifecycleController = new FdLifecycleController(jdbc, null, json, clock);

        when(actor.getName()).thenReturn("test-user");
        when(actor.getDetails()).thenReturn(Map.of("userId", userId));
    }

    private String sha256(String s) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =========================================================================
    // 1. FD CALCULATOR TESTS: Precision, Category Benefits, Caps & Alignment
    // =========================================================================

    @Test
    @DisplayName("Calculator: Senior Citizen boost (+0.50%) applied with BigDecimal precision")
    void calculatorAppliesSeniorCitizenBoost() {
        FdCalculatorController.EstimationRequest req = new FdCalculatorController.EstimationRequest(
                new BigDecimal("10000.00"), new BigDecimal("5.00"), 365,
                FdCalculatorController.CustomerCategory.SENIOR_CITIZEN, "QUARTERLY");

        FdCalculatorController.EstimationResponse res = calculatorController.estimate(req);
        assertEquals(new BigDecimal("5.50"), res.effectiveInterestRate());
        assertEquals(new BigDecimal("10000.00"), res.principal());
        assertTrue(res.maturityAmount().compareTo(new BigDecimal("10500.00")) > 0);
        assertTrue(res.totalInterest().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Calculator: Multiple categories combined (+0.50% + +0.75% = +1.25%)")
    void calculatorCombinesMultipleCategories() {
        FdCalculatorController.EstimationRequest req = new FdCalculatorController.EstimationRequest(
                new BigDecimal("10000.00"), new BigDecimal("6.00"), 365,
                null, List.of(FdCalculatorController.CustomerCategory.SENIOR_CITIZEN, FdCalculatorController.CustomerCategory.EMPLOYEE),
                "INR", null, "QUARTERLY");

        FdCalculatorController.EstimationResponse res = calculatorController.estimate(req);
        assertEquals(new BigDecimal("7.25"), res.effectiveInterestRate()); // 6.00 + 1.25
    }

    @Test
    @DisplayName("Calculator: Category boost capped at 2.00% and effective rate capped at 8.50%")
    void calculatorEnforcesCategoryCapAndEffectiveCeiling() {
        // Base 7.50% + category 1.25% would be 8.75%, but capped to 8.50%
        FdCalculatorController.EstimationRequest req = new FdCalculatorController.EstimationRequest(
                new BigDecimal("10000.00"), new BigDecimal("7.50"), 365,
                null, List.of(FdCalculatorController.CustomerCategory.SENIOR_CITIZEN, FdCalculatorController.CustomerCategory.EMPLOYEE),
                "INR", null, "QUARTERLY");

        FdCalculatorController.EstimationResponse res = calculatorController.estimate(req);
        assertEquals(new BigDecimal("8.50"), res.effectiveInterestRate());
    }

    @Test
    @DisplayName("Calculator: Currency scale respected (JPY 0-decimal scale vs KWD 3-decimal scale)")
    void calculatorRespectsCurrencyScale() {
        when(jdbc.query(contains("fcr_decimal_places"), any(ResultSetExtractor.class), eq("JPY")))
                .thenReturn(0);
        when(jdbc.query(contains("fcr_decimal_places"), any(ResultSetExtractor.class), eq("KWD")))
                .thenReturn(3);

        FdCalculatorController.EstimationRequest jpyReq = new FdCalculatorController.EstimationRequest(
                new BigDecimal("100000"), new BigDecimal("1.00"), 365,
                FdCalculatorController.CustomerCategory.STANDARD, null, "JPY", null, "QUARTERLY");
        FdCalculatorController.EstimationResponse jpyRes = calculatorController.estimate(jpyReq);
        assertEquals(0, jpyRes.maturityAmount().scale());
        assertEquals(0, jpyRes.totalInterest().scale());

        FdCalculatorController.EstimationRequest kwdReq = new FdCalculatorController.EstimationRequest(
                new BigDecimal("100.000"), new BigDecimal("4.00"), 365,
                FdCalculatorController.CustomerCategory.STANDARD, null, "KWD", null, "QUARTERLY");
        FdCalculatorController.EstimationResponse kwdRes = calculatorController.estimate(kwdReq);
        assertEquals(3, kwdRes.maturityAmount().scale());
        assertEquals(3, kwdRes.totalInterest().scale());
    }

    @Test
    @DisplayName("Calculator: Aligns exactly with FdCompounding maturity calculation")
    void calculatorMatchesFdCompoundingEngineExactly() {
        BigDecimal principal = new BigDecimal("50000.00");
        BigDecimal ratePercent = new BigDecimal("6.50");
        int days = 180;
        String basis = "QUARTERLY";
        int scale = 2;

        FdCalculatorController.EstimationRequest req = new FdCalculatorController.EstimationRequest(
                principal, ratePercent, days,
                FdCalculatorController.CustomerCategory.STANDARD, null, "INR", null, basis);
        FdCalculatorController.EstimationResponse res = calculatorController.estimate(req);

        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(days);
        BigDecimal expectedMaturity = FdCompounding.maturity(
                principal, ratePercent.divide(new BigDecimal("100"), 12, RoundingMode.HALF_UP),
                start, end, scale, basis);

        assertEquals(expectedMaturity, res.maturityAmount());
    }

    // =========================================================================
    // 2. BOOKING VALIDATION TESTS: Eligibility, Inactive Product, Bounds
    // =========================================================================

    private void mockKycVerified(long uid) {
        when(jdbc.query(contains("fur_kyc_status"), any(ResultSetExtractor.class), eq(uid)))
                .thenReturn(null); // null message means verified & not HIGH risk
    }

    @Test
    @DisplayName("Booking: Rejects unverified KYC customer with 403 FORBIDDEN")
    void bookingRejectsUnverifiedCustomer() {
        when(jdbc.query(contains("fur_kyc_status"), any(ResultSetExtractor.class), eq(userId)))
                .thenReturn("Your KYC verification is still pending.");

        FixedDepositController.BookingRequest req = new FixedDepositController.BookingRequest(
                1L, 10L, new BigDecimal("1000.00"), "INR", 12);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                depositController.book(actor, "key-kyc-1", req));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("Booking: Rejects inactive product with 400 BAD_REQUEST")
    void bookingRejectsInactiveProduct() {
        mockKycVerified(userId);
        when(jdbc.query(contains("SELECT fpr_product_code"), any(ResultSetExtractor.class), eq(99L)))
                .thenReturn(Map.of("code", "FD_INACTIVE", "name", "Inactive FD", "status", "INACTIVE"));

        FixedDepositController.BookingRequest req = new FixedDepositController.BookingRequest(
                99L, 10L, new BigDecimal("1000.00"), "INR", 12);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                depositController.book(actor, "key-inactive-1", req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("product is not active"));
    }

    @Test
    @DisplayName("Booking: Rejects unsupported currency for product with 400 BAD_REQUEST")
    void bookingRejectsUnsupportedCurrency() {
        mockKycVerified(userId);
        when(jdbc.query(contains("SELECT fpr_product_code"), any(ResultSetExtractor.class), eq(1L)))
                .thenReturn(Map.of("code", "FD_STD", "name", "Standard FD", "status", "ACTIVE"));
        when(jdbc.query(contains("SELECT fcr_currency_id"), any(ResultSetExtractor.class), eq("EUR")))
                .thenReturn(Map.of("id", 5L, "scale", 2));
        when(jdbc.query(contains("SELECT sa_account_id FROM savings_accounts"), any(ResultSetExtractor.class), eq(10L), eq(userId), eq(5L)))
                .thenReturn(Map.of("id", 10L));
        // Currency not in product currency config
        when(jdbc.query(contains("SELECT minimum_amount,maximum_amount"), any(ResultSetExtractor.class), eq(1L), eq(5L)))
                .thenReturn(null);

        FixedDepositController.BookingRequest req = new FixedDepositController.BookingRequest(
                1L, 10L, new BigDecimal("1000.00"), "EUR", 12);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                depositController.book(actor, "key-curr-1", req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("not supported for this product"));
    }

    @Test
    @DisplayName("Booking: Rejects principal outside permitted bounds [min, max] with 400 BAD_REQUEST")
    void bookingRejectsPrincipalOutOfBounds() {
        mockKycVerified(userId);
        when(jdbc.query(contains("SELECT fpr_product_code"), any(ResultSetExtractor.class), eq(1L)))
                .thenReturn(Map.of("code", "FD_STD", "name", "Standard FD", "status", "ACTIVE"));
        when(jdbc.query(contains("SELECT fcr_currency_id"), any(ResultSetExtractor.class), eq("INR")))
                .thenReturn(Map.of("id", 1L, "scale", 2));
        when(jdbc.query(contains("SELECT sa_account_id FROM savings_accounts"), any(ResultSetExtractor.class), eq(10L), eq(userId), eq(1L)))
                .thenReturn(Map.of("id", 10L));
        when(jdbc.query(contains("SELECT minimum_amount,maximum_amount"), any(ResultSetExtractor.class), eq(1L), eq(1L)))
                .thenReturn(Map.of("min", new BigDecimal("1000.00"), "max", new BigDecimal("100000.00"), "penalty", "1.0000"));

        // Case 1: Below minimum (500 < 1000)
        FixedDepositController.BookingRequest reqBelow = new FixedDepositController.BookingRequest(
                1L, 10L, new BigDecimal("500.00"), "INR", 12);
        ResponseStatusException exBelow = assertThrows(ResponseStatusException.class, () ->
                depositController.book(actor, "key-bound-1", reqBelow));
        assertEquals(HttpStatus.BAD_REQUEST, exBelow.getStatusCode());
        assertTrue(exBelow.getReason().contains("outside permitted bounds"));

        // Case 2: Above maximum (200000 > 100000)
        FixedDepositController.BookingRequest reqAbove = new FixedDepositController.BookingRequest(
                1L, 10L, new BigDecimal("200000.00"), "INR", 12);
        ResponseStatusException exAbove = assertThrows(ResponseStatusException.class, () ->
                depositController.book(actor, "key-bound-2", reqAbove));
        assertEquals(HttpStatus.BAD_REQUEST, exAbove.getStatusCode());
        assertTrue(exAbove.getReason().contains("outside permitted bounds"));
    }

    @Test
    @DisplayName("Booking: Rejects tenure outside configured rate bands with 400 BAD_REQUEST")
    void bookingRejectsTenureOutsideBands() {
        mockKycVerified(userId);
        when(jdbc.query(contains("SELECT fpr_product_code"), any(ResultSetExtractor.class), eq(1L)))
                .thenReturn(Map.of("code", "FD_STD", "name", "Standard FD", "status", "ACTIVE"));
        when(jdbc.query(contains("SELECT fcr_currency_id"), any(ResultSetExtractor.class), eq("INR")))
                .thenReturn(Map.of("id", 1L, "scale", 2));
        when(jdbc.query(contains("SELECT sa_account_id FROM savings_accounts"), any(ResultSetExtractor.class), eq(10L), eq(userId), eq(1L)))
                .thenReturn(Map.of("id", 10L));
        when(jdbc.query(contains("SELECT minimum_amount,maximum_amount"), any(ResultSetExtractor.class), eq(1L), eq(1L)))
                .thenReturn(Map.of("min", new BigDecimal("1000.00"), "max", new BigDecimal("100000.00"), "penalty", "1.0000"));
        // No rate band matches 120 months
        when(jdbc.queryForList(contains("SELECT annual_rate,compounding_basis"), eq(1L), eq(1L), eq(120), eq(120)))
                .thenReturn(List.of());

        FixedDepositController.BookingRequest req = new FixedDepositController.BookingRequest(
                1L, 10L, new BigDecimal("5000.00"), "INR", 120);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                depositController.book(actor, "key-tenure-1", req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("does not match any configured tenure rate band"));
    }

    // =========================================================================
    // 3. IDEMPOTENCY & REPEATED KEYS
    // =========================================================================

    @Test
    @DisplayName("Idempotency: Replaying exact same request with same key returns existing booking")
    void idempotencyReplaysExistingBooking() {
        mockKycVerified(userId);
        // Simulate DuplicateKeyException on claim, with matching hash and COMPLETED status
        doThrow(new DuplicateKeyException("duplicate")).when(jdbc).update(contains("INSERT INTO fd_operation_idempotency"), any(), any(), any(), any());
        String expectedHash = sha256("1|10|5000.00|INR|12");
        when(jdbc.queryForMap(contains("SELECT foi_request_hash,foi_status,foi_aggregate_id"), any(), any(), eq("same-key-1")))
                .thenReturn(Map.of("foi_request_hash", expectedHash, "foi_status", "COMPLETED", "foi_aggregate_id", 101L));

        // find(101L, userId)
        when(jdbc.query(contains("SELECT a.fa_account_id"), any(ResultSetExtractor.class), eq(101L), eq(userId)))
                .thenReturn(new FixedDepositController.FdView(101L, "FD-101", new BigDecimal("5000.00"), 1L, "INR", 12, new BigDecimal("0.0650"), LocalDate.now(), LocalDate.now().plusMonths(12), new BigDecimal("5332.00"), "PENDING", 10L, 1L, "FD_STD", "Standard FD", "QUARTERLY", "AT_MATURITY", "policy-v2"));

        FixedDepositController.BookingRequest req = new FixedDepositController.BookingRequest(
                1L, 10L, new BigDecimal("5000.00"), "INR", 12);

        FixedDepositController.FdView view = depositController.book(actor, "same-key-1", req);
        assertNotNull(view);
        assertEquals(101L, view.accountId());
    }

    @Test
    @DisplayName("Idempotency: Modifying request payload with already used key throws 409 CONFLICT")
    void idempotencyRejectsModifiedPayloadWithSameKey() {
        mockKycVerified(userId);
        doThrow(new DuplicateKeyException("duplicate")).when(jdbc).update(contains("INSERT INTO fd_operation_idempotency"), any(), any(), any(), any());
        // Stored request hash differs
        when(jdbc.queryForMap(contains("SELECT foi_request_hash,foi_status,foi_aggregate_id"), any(), any(), eq("same-key-1")))
                .thenReturn(Map.of("foi_request_hash", "different-stored-hash", "foi_status", "COMPLETED", "foi_aggregate_id", 101L));

        FixedDepositController.BookingRequest req = new FixedDepositController.BookingRequest(
                1L, 10L, new BigDecimal("5000.00"), "INR", 12);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                depositController.book(actor, "same-key-1", req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already used for a different request"));
    }

    // =========================================================================
    // 4. FROZEN TERMS SNAPSHOT & PRODUCT CHANGES AFTER BOOKING
    // =========================================================================

    private Map<String, Object> fdRow(long owner, long product, BigDecimal principal, long currency,
                                      String status, long savings, BigDecimal maturityAmount, BigDecimal rate,
                                      Timestamp opened, Date maturity, String termsSnapshot) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("owner", owner);
        map.put("product", product);
        map.put("principal", principal);
        map.put("currency", currency);
        map.put("status", status);
        map.put("savings", savings);
        map.put("maturityAmount", maturityAmount);
        map.put("rate", rate);
        map.put("opened", opened);
        map.put("maturity", maturity);
        map.put("termsSnapshot", termsSnapshot);
        return map;
    }

    @Test
    @DisplayName("Premature Closure: Reads frozen penalty from terms snapshot, immune to product edits after booking")
    void prematureClosureUsesFrozenPenaltySnapshotEvenIfProductChanges() {
        long fdId = 200L;
        // Snapshot frozen at booking time with prematurePenaltyPoints = 1.0000
        String frozenSnapshot = "{\"productId\":1,\"annualRate\":0.0650,\"prematurePenaltyPoints\":\"1.0000\",\"principal\":10000.00}";

        when(jdbc.query(contains("SELECT fa_primary_holder_id,fa_product_id"), any(ResultSetExtractor.class), eq(fdId)))
                .thenReturn(fdRow(
                        userId,
                        1L,
                        new BigDecimal("10000.00"),
                        1L,
                        "ACTIVE",
                        10L,
                        new BigDecimal("10660.00"),
                        new BigDecimal("0.0650"),
                        Timestamp.valueOf(LocalDate.now().minusDays(90).atStartOfDay()),
                        Date.valueOf(LocalDate.now().plusDays(90)),
                        frozenSnapshot
                ));
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM fd_premature_closure"), eq(Integer.class), eq(fdId)))
                .thenReturn(0);
        when(jdbc.queryForObject(contains("fcr_decimal_places"), eq(Integer.class), eq(1L)))
                .thenReturn(2);
        when(jdbc.queryForObject(contains("LAST_INSERT_ID"), eq(Long.class)))
                .thenReturn(501L);

        // Even if fd_product_currency_ref was mutated after booking to 5.0000 penalty points,
        // the quote method should NEVER read it because frozenSnapshot already has 1.0000!
        when(jdbc.query(contains("SELECT premature_penalty_points FROM fd_product_currency_ref"), any(ResultSetExtractor.class), any(), any()))
                .thenReturn(Map.of("points", "5.0000")); // altered live table

        // quote response
        when(jdbc.query(contains("SELECT c.fpc_account_id,c.fpc_penalty_pct_applied"), any(ResultSetExtractor.class), eq(501L), eq(userId)))
                .thenReturn(new FdLifecycleController.PrematureQuote(501L, fdId, new BigDecimal("1.0000"), new BigDecimal("25.00"), new BigDecimal("10135.00"), "PENDING", LocalDate.now().toString(), null, new BigDecimal("0.0550"), "INR"));

        FdLifecycleController.PrematureQuote quote = lifecycleController.requestPremature(
                actor, fdId, "quote-key-1", new FdLifecycleController.PrematureRequest("Emergency cash"));

        assertNotNull(quote);
        assertEquals(new BigDecimal("1.0000"), quote.penaltyPoints());
        // Verify fd_product_currency_ref query was NOT executed because snapshot took precedence
        verify(jdbc, never()).query(contains("SELECT premature_penalty_points FROM fd_product_currency_ref"), any(ResultSetExtractor.class), any(), any());
    }

    @Test
    @DisplayName("Premature Closure: Fallback to fd_product_currency_ref for legacy records without penalty in snapshot")
    void prematureClosureFallsBackToLiveTableForLegacyRecordsWithoutSnapshotPenalty() {
        long fdId = 201L;
        // Legacy record snapshot without prematurePenaltyPoints
        String legacySnapshot = "{\"productId\":1,\"annualRate\":0.0650,\"principal\":10000.00}";

        when(jdbc.query(contains("SELECT fa_primary_holder_id,fa_product_id"), any(ResultSetExtractor.class), eq(fdId)))
                .thenReturn(fdRow(
                        userId,
                        1L,
                        new BigDecimal("10000.00"),
                        1L,
                        "ACTIVE",
                        10L,
                        new BigDecimal("10660.00"),
                        new BigDecimal("0.0650"),
                        Timestamp.valueOf(LocalDate.now().minusDays(90).atStartOfDay()),
                        Date.valueOf(LocalDate.now().plusDays(90)),
                        legacySnapshot
                ));
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM fd_premature_closure"), eq(Integer.class), eq(fdId)))
                .thenReturn(0);
        when(jdbc.queryForObject(contains("fcr_decimal_places"), eq(Integer.class), eq(1L)))
                .thenReturn(2);
        when(jdbc.queryForObject(contains("LAST_INSERT_ID"), eq(Long.class)))
                .thenReturn(502L);

        // Fallback live query is executed
        when(jdbc.query(contains("SELECT premature_penalty_points FROM fd_product_currency_ref"), any(ResultSetExtractor.class), eq(1L), eq(1L)))
                .thenReturn(Map.of("points", "1.5000"));

        when(jdbc.query(contains("SELECT c.fpc_account_id,c.fpc_penalty_pct_applied"), any(ResultSetExtractor.class), eq(502L), eq(userId)))
                .thenReturn(new FdLifecycleController.PrematureQuote(502L, fdId, new BigDecimal("1.5000"), new BigDecimal("35.00"), new BigDecimal("10125.00"), "PENDING", LocalDate.now().toString(), null, new BigDecimal("0.0500"), "INR"));

        FdLifecycleController.PrematureQuote quote = lifecycleController.requestPremature(
                actor, fdId, "quote-key-2", new FdLifecycleController.PrematureRequest("Legacy withdrawal"));

        assertNotNull(quote);
        assertEquals(new BigDecimal("1.5000"), quote.penaltyPoints());
        // Verify fallback query was executed exactly once for legacy record
        verify(jdbc, times(1)).query(contains("SELECT premature_penalty_points FROM fd_product_currency_ref"), any(ResultSetExtractor.class), eq(1L), eq(1L));
    }
}
