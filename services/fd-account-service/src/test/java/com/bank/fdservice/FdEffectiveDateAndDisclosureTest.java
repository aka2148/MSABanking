package com.bank.fdservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FdEffectiveDateAndDisclosureTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private Authentication actor;
    @Mock private MpinProofVerifier mpin;

    private ObjectMapper json;
    private DemoClockService clock;
    private FixedDepositController depositController;
    private FdCalculatorController calculatorController;

    private final long userId = 42L;
    private final LocalDate businessDate = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));

    @BeforeEach
    void setUp() {
        json = new ObjectMapper();
        clock = new DemoClockService(jdbc, "Asia/Kolkata");
        depositController = new FixedDepositController(jdbc, json, clock, mpin);
        calculatorController = new FdCalculatorController(jdbc);

        when(actor.getName()).thenReturn("test-user");
        when(actor.getDetails()).thenReturn(Map.of("userId", userId, "kycStatus", "VERIFIED", "riskCategory", "LOW"));
        SecurityContextHolder.getContext().setAuthentication(actor);

        // Default clock mock (offset 0 -> businessDate is today)
        when(jdbc.queryForObject(contains("SELECT fdc_offset_days FROM fd_demo_clock"), eq(Integer.class)))
                .thenReturn(0);

        // Default KYC
        when(jdbc.query(contains("SELECT fcp_kyc_status"), any(ResultSetExtractor.class), eq(userId)))
                .thenReturn(Map.of("kyc", "VERIFIED", "risk", "LOW"));

        // Default Currency INR
        when(jdbc.query(contains("SELECT fcr_currency_id"), any(ResultSetExtractor.class), eq("INR")))
                .thenReturn(Map.of("id", 1L, "scale", 2));
        when(jdbc.queryForObject(contains("SELECT fcr_decimal_places"), eq(Integer.class), eq(1L)))
                .thenReturn(2);
        when(jdbc.query(contains("SELECT fcr_decimal_places FROM fd_currency_ref WHERE UPPER(fcr_iso_code)"), any(ResultSetExtractor.class), eq("INR")))
                .thenReturn(2);

        // Default Product
        when(jdbc.query(contains("SELECT fpr_product_code"), any(ResultSetExtractor.class), eq(1L)))
                .thenReturn(Map.of("code", "FD_STD", "name", "Standard FD", "status", "ACTIVE"));

        // Default Savings Account
        when(jdbc.query(contains("SELECT sa_account_id FROM savings_accounts"), any(ResultSetExtractor.class), eq(10L), eq(userId), eq(1L)))
                .thenReturn(Map.of("id", 10L));

        // Default Product Currency Config (min 1000, max 100000, penalty 1.0000)
        when(jdbc.query(contains("SELECT minimum_amount,maximum_amount"), any(ResultSetExtractor.class), eq(1L), eq(1L)))
                .thenReturn(Map.of("min", new BigDecimal("1000.00"), "max", new BigDecimal("100000.00"), "penalty", "1.0000"));

        // Idempotency claim default (returns null for new claim)
        when(jdbc.update(contains("INSERT INTO fd_operation_idempotency"), any(), any(), any(), any()))
                .thenReturn(1);

        // Insert FD account default ID
        when(jdbc.queryForObject(eq("SELECT LAST_INSERT_ID()"), eq(Long.class))).thenReturn(101L);

        // Mock find(id, user)
        when(jdbc.query(contains("FROM fd_accounts a JOIN fd_currency_ref c"), any(ResultSetExtractor.class), eq(101L), eq(userId)))
                .thenReturn(new FixedDepositController.FdView(
                        101L, "FD-TEST12345678", new BigDecimal("10000.00"), 1L, "INR", 12,
                        new BigDecimal("0.0750"), businessDate, businessDate.plusMonths(12),
                        new BigDecimal("10771.36"), "PENDING", 10L, 1L, "FD_STD", "Standard FD",
                        "QUARTERLY", "AT_MATURITY", "academic-v2-quarterly", false
                ));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Map<String, Object> band(BigDecimal rate, String comp, String payout, LocalDate from, LocalDate to) {
        Map<String, Object> map = new HashMap<>();
        map.put("annual_rate", rate);
        map.put("compounding_basis", comp);
        map.put("payout_frequency", payout);
        map.put("effective_from", from == null ? null : Date.valueOf(from));
        map.put("effective_to", to == null ? null : Date.valueOf(to));
        return map;
    }

    // =========================================================================
    // FEATURE A: RATE DISCLOSURE VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("Feature A: Booking with matching disclosed annual rate succeeds and captures disclosure in snapshot")
    void booking_withMatchingDisclosedRate_succeedsAndRecordsDisclosure() {
        when(jdbc.queryForList(contains("FROM fd_product_rate_ref WHERE product_id=?"), eq(1L), eq(1L), eq(12), eq(12)))
                .thenReturn(List.of(band(new BigDecimal("0.0750"), "QUARTERLY", "AT_MATURITY", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))));

        FixedDepositController.BookingRequest req = new FixedDepositController.BookingRequest(
                1L, 10L, new BigDecimal("10000.00"), "INR", 12, new BigDecimal("0.0750"));

        FixedDepositController.FdView view = depositController.book(actor, "key-disc-match", req);

        assertNotNull(view);
        assertEquals(0, new BigDecimal("0.0750").compareTo(view.annualRate()));

        // Capture SQL insert to verify terms snapshot
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(contains("INSERT INTO fd_accounts"),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), captor.capture(), any());
        String termsJson = (String) captor.getValue();
        assertTrue(termsJson.contains("\"disclosedAnnualRate\":0.0750"));
        assertTrue(termsJson.contains("\"disclosureVerified\":true"));
        assertTrue(termsJson.contains("\"calculationConvention\":\"ACTUAL_365\""));
    }

    @Test
    @DisplayName("Feature A: Booking with mismatched disclosed rate (stale rate quote) throws 409 CONFLICT")
    void booking_withMismatchedDisclosedRate_failsWith409Conflict() {
        when(jdbc.queryForList(contains("FROM fd_product_rate_ref WHERE product_id=?"), eq(1L), eq(1L), eq(12), eq(12)))
                .thenReturn(List.of(band(new BigDecimal("0.0750"), "QUARTERLY", "AT_MATURITY", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))));

        // Client presents stale quote of 7.00% when active rate is 7.50%
        FixedDepositController.BookingRequest req = new FixedDepositController.BookingRequest(
                1L, 10L, new BigDecimal("10000.00"), "INR", 12, new BigDecimal("0.0700"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                depositController.book(actor, "key-disc-mismatch", req));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Disclosed interest rate (0.0700) differs from authoritative rate (0.0750)"));
    }

    @Test
    @DisplayName("Feature A: Booking without disclosed rate succeeds for backward compatibility")
    void booking_withoutDisclosedRate_backwardCompatible_succeeds() {
        when(jdbc.queryForList(contains("FROM fd_product_rate_ref WHERE product_id=?"), eq(1L), eq(1L), eq(12), eq(12)))
                .thenReturn(List.of(band(new BigDecimal("0.0750"), "QUARTERLY", "AT_MATURITY", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))));

        // No expected_annual_rate passed
        FixedDepositController.BookingRequest req = new FixedDepositController.BookingRequest(
                1L, 10L, new BigDecimal("10000.00"), "INR", 12);

        FixedDepositController.FdView view = depositController.book(actor, "key-disc-compat", req);

        assertNotNull(view);
        assertEquals(0, new BigDecimal("0.0750").compareTo(view.annualRate()));
    }

    // =========================================================================
    // FEATURE B: EFFECTIVE DATE EVALUATION OF APPLICABLE PRICING SCHEDULES
    // =========================================================================

    @Test
    @DisplayName("Feature B: Booking on inclusive start date boundary succeeds")
    void booking_onEffectiveFromBoundary_succeeds() {
        when(jdbc.queryForList(contains("FROM fd_product_rate_ref WHERE product_id=?"), eq(1L), eq(1L), eq(12), eq(12)))
                .thenReturn(List.of(band(new BigDecimal("0.0750"), "QUARTERLY", "AT_MATURITY", businessDate, businessDate.plusMonths(6))));

        FixedDepositController.BookingRequest req = new FixedDepositController.BookingRequest(
                1L, 10L, new BigDecimal("10000.00"), "INR", 12, new BigDecimal("0.0750"));

        FixedDepositController.FdView view = depositController.book(actor, "key-bound-start", req);
        assertNotNull(view);
    }

    @Test
    @DisplayName("Feature B: Booking on inclusive end date boundary succeeds")
    void booking_onEffectiveToBoundary_succeeds() {
        when(jdbc.queryForList(contains("FROM fd_product_rate_ref WHERE product_id=?"), eq(1L), eq(1L), eq(12), eq(12)))
                .thenReturn(List.of(band(new BigDecimal("0.0750"), "QUARTERLY", "AT_MATURITY", businessDate.minusMonths(6), businessDate)));

        FixedDepositController.BookingRequest req = new FixedDepositController.BookingRequest(
                1L, 10L, new BigDecimal("10000.00"), "INR", 12, new BigDecimal("0.0750"));

        FixedDepositController.FdView view = depositController.book(actor, "key-bound-end", req);
        assertNotNull(view);
    }

    @Test
    @DisplayName("Feature B: Booking before schedule starts throws 400 NOT_YET_EFFECTIVE")
    void booking_beforeScheduleStart_failsWithNotYetEffective() {
        LocalDate futureStart = businessDate.plusDays(10);
        when(jdbc.queryForList(contains("FROM fd_product_rate_ref WHERE product_id=?"), eq(1L), eq(1L), eq(12), eq(12)))
                .thenReturn(List.of(band(new BigDecimal("0.0750"), "QUARTERLY", "AT_MATURITY", futureStart, futureStart.plusYears(1))));

        FixedDepositController.BookingRequest req = new FixedDepositController.BookingRequest(
                1L, 10L, new BigDecimal("10000.00"), "INR", 12);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                depositController.book(actor, "key-future", req));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("NOT_YET_EFFECTIVE"));
        assertTrue(ex.getReason().contains(futureStart.toString()));
    }

    @Test
    @DisplayName("Feature B: Booking after schedule expired throws 400 EXPIRED")
    void booking_afterScheduleEnd_failsWithExpired() {
        LocalDate pastEnd = businessDate.minusDays(1);
        when(jdbc.queryForList(contains("FROM fd_product_rate_ref WHERE product_id=?"), eq(1L), eq(1L), eq(12), eq(12)))
                .thenReturn(List.of(band(new BigDecimal("0.0750"), "QUARTERLY", "AT_MATURITY", pastEnd.minusYears(1), pastEnd)));

        FixedDepositController.BookingRequest req = new FixedDepositController.BookingRequest(
                1L, 10L, new BigDecimal("10000.00"), "INR", 12);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                depositController.book(actor, "key-expired", req));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("EXPIRED"));
        assertTrue(ex.getReason().contains(pastEnd.toString()));
    }

    @Test
    @DisplayName("Feature B: Ambiguous overlapping schedules with differing rates throw 409 CONFLICT")
    void booking_withAmbiguousOverlappingSchedules_failsWith409Conflict() {
        // Two active overlapping schedules with conflicting rates (7.50% and 8.00%)
        when(jdbc.queryForList(contains("FROM fd_product_rate_ref WHERE product_id=?"), eq(1L), eq(1L), eq(12), eq(12)))
                .thenReturn(List.of(
                        band(new BigDecimal("0.0750"), "QUARTERLY", "AT_MATURITY", businessDate.minusMonths(1), businessDate.plusMonths(6)),
                        band(new BigDecimal("0.0800"), "QUARTERLY", "AT_MATURITY", businessDate.minusMonths(2), businessDate.plusMonths(3))
                ));

        FixedDepositController.BookingRequest req = new FixedDepositController.BookingRequest(
                1L, 10L, new BigDecimal("10000.00"), "INR", 12);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                depositController.book(actor, "key-ambig", req));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("AMBIGUOUS_CONFIGURATION"));
    }

    @Test
    @DisplayName("Feature B: Calculator candidate band lookup filters by effective dates and detects ambiguity")
    void calculator_evaluatesEffectiveDatesAndAmbiguity() {
        // 1. Ambiguous overlapping candidate schedules in calculator
        when(jdbc.queryForList(contains("FROM fd_product_rate_ref rr JOIN fd_currency_ref c"), eq(1L), eq("INR"), eq(12), eq(12)))
                .thenReturn(List.of(
                        band(new BigDecimal("0.0750"), "QUARTERLY", "AT_MATURITY", LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6)),
                        band(new BigDecimal("0.0800"), "QUARTERLY", "AT_MATURITY", LocalDate.now().minusMonths(2), LocalDate.now().plusMonths(3))
                ));

        FdCalculatorController.EstimationRequest req = new FdCalculatorController.EstimationRequest(
                new BigDecimal("10000.00"), null, 365, null, null, "INR", 1L, "QUARTERLY");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                calculatorController.estimate(req));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("AMBIGUOUS_CONFIGURATION"));

        // 2. Active candidate schedule in calculator resolves base rate correctly
        when(jdbc.queryForList(contains("FROM fd_product_rate_ref rr JOIN fd_currency_ref c"), eq(1L), eq("INR"), eq(12), eq(12)))
                .thenReturn(List.of(
                        band(new BigDecimal("0.0750"), "QUARTERLY", "AT_MATURITY", LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6))
                ));

        FdCalculatorController.EstimationResponse res = calculatorController.estimate(req);
        assertEquals(new BigDecimal("7.50"), res.effectiveInterestRate());
    }

    @Test
    @DisplayName("Feature B: Booked terms snapshot remains immutable when product rates change subsequently")
    void booking_termsSnapshot_remainsImmutableWhenProductRateChangesLater() {
        // Step 1: Book deposit with 7.50%
        when(jdbc.queryForList(contains("FROM fd_product_rate_ref WHERE product_id=?"), eq(1L), eq(1L), eq(12), eq(12)))
                .thenReturn(List.of(band(new BigDecimal("0.0750"), "QUARTERLY", "AT_MATURITY", businessDate.minusMonths(1), businessDate.plusMonths(6))));

        FixedDepositController.BookingRequest req = new FixedDepositController.BookingRequest(
                1L, 10L, new BigDecimal("10000.00"), "INR", 12, new BigDecimal("0.0750"));

        FixedDepositController.FdView view = depositController.book(actor, "key-immut", req);
        assertNotNull(view);
        assertEquals(0, new BigDecimal("0.0750").compareTo(view.annualRate()));

        // Step 2: Rate schedule is subsequently changed to 8.50%
        when(jdbc.queryForList(contains("FROM fd_product_rate_ref WHERE product_id=?"), eq(1L), eq(1L), eq(12), eq(12)))
                .thenReturn(List.of(band(new BigDecimal("0.0850"), "QUARTERLY", "AT_MATURITY", businessDate.minusMonths(1), businessDate.plusMonths(6))));

        // Step 3: Verify the booked account retains its original terms
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(contains("INSERT INTO fd_accounts"),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), captor.capture(), any());
        String termsJson = (String) captor.getValue();
        assertTrue(termsJson.contains("\"annualRate\":0.0750"));
        assertFalse(termsJson.contains("0.0850"));
    }
}
