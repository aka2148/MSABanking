package com.bank.productservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FdComplianceServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    private FdComplianceService complianceService;

    @BeforeEach
    void setUp() {
        complianceService = new FdComplianceService(jdbc);
    }

    private record MockCandidate(
            LocalDate from,
            LocalDate to,
            BigDecimal rate,
            String compounding,
            String payout,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Integer minMonths,
            Integer maxMonths
    ) {}

    @SuppressWarnings("unchecked")
    private void mockCandidateData(List<MockCandidate> candidates) {
        when(jdbc.query(anyString(), any(RowMapper.class), anyLong(), anyString()))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    List<Object> result = new ArrayList<>();
                    for (int i = 0; i < candidates.size(); i++) {
                        MockCandidate c = candidates.get(i);
                        ResultSet rs = mock(ResultSet.class);
                        when(rs.getObject("effective_from", LocalDate.class)).thenReturn(c.from);
                        when(rs.getObject("effective_to", LocalDate.class)).thenReturn(c.to);
                        when(rs.getBigDecimal("annual_rate")).thenReturn(c.rate);
                        when(rs.getString("ro_compounding_basis")).thenReturn(c.compounding);
                        when(rs.getString("ro_payout_frequency")).thenReturn(c.payout);
                        when(rs.getBigDecimal("min_amount")).thenReturn(c.minAmount);
                        when(rs.getBigDecimal("max_amount")).thenReturn(c.maxAmount);
                        when(rs.getObject("min_months", Integer.class)).thenReturn(c.minMonths);
                        when(rs.getObject("max_months", Integer.class)).thenReturn(c.maxMonths);
                        result.add(mapper.mapRow(rs, i));
                    }
                    return result;
                });
    }

    @Test
    @DisplayName("verifyRate: Disclosed rate matches authoritative rate on evaluation date")
    void verifyRate_matchingRate_returnsMatch() {
        LocalDate evalDate = LocalDate.of(2026, 6, 1);
        mockCandidateData(List.of(
                new MockCandidate(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 12, 31),
                        new BigDecimal("0.0750"),
                        "QUARTERLY",
                        "AT_MATURITY",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000000.00"),
                        1,
                        36
                )
        ));

        FdComplianceService.RateDisclosureRequest request = new FdComplianceService.RateDisclosureRequest(
                1L, "INR", new BigDecimal("50000.00"), 12, new BigDecimal("0.0750"), evalDate);

        FdComplianceService.RateDisclosureResponse response = complianceService.verifyRate(request, evalDate);

        assertEquals("MATCH", response.matchStatus());
        assertTrue(response.isMatch());
        assertEquals(0, new BigDecimal("0.0750").compareTo(response.authoritativeRate()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.difference()));
        assertEquals("QUARTERLY", response.compoundingFrequency());
    }

    @Test
    @DisplayName("verifyRate: Disclosed rate differs from authoritative rate (stale quote) returns MISMATCH")
    void verifyRate_mismatchedRate_returnsMismatch() {
        LocalDate evalDate = LocalDate.of(2026, 6, 1);
        mockCandidateData(List.of(
                new MockCandidate(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 12, 31),
                        new BigDecimal("0.0750"),
                        "QUARTERLY",
                        "AT_MATURITY",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000000.00"),
                        1,
                        36
                )
        ));

        // Client presents stale rate of 7.00% when active rate is 7.50%
        FdComplianceService.RateDisclosureRequest request = new FdComplianceService.RateDisclosureRequest(
                1L, "INR", new BigDecimal("50000.00"), 12, new BigDecimal("0.0700"), evalDate);

        FdComplianceService.RateDisclosureResponse response = complianceService.verifyRate(request, evalDate);

        assertEquals("MISMATCH", response.matchStatus());
        assertFalse(response.isMatch());
        assertEquals(0, new BigDecimal("0.0750").compareTo(response.authoritativeRate()));
        assertEquals(new BigDecimal("-0.0050"), response.difference());
    }

    @Test
    @DisplayName("verifyRate: No active rate on evaluation date returns NO_APPLICABLE_RATE")
    void verifyRate_noActiveRate_returnsNoApplicableRate() {
        LocalDate evalDate = LocalDate.of(2026, 6, 1);
        // Candidate expired in 2025
        mockCandidateData(List.of(
                new MockCandidate(
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 12, 31),
                        new BigDecimal("0.0700"),
                        "QUARTERLY",
                        "AT_MATURITY",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000000.00"),
                        1,
                        36
                )
        ));

        FdComplianceService.RateDisclosureRequest request = new FdComplianceService.RateDisclosureRequest(
                1L, "INR", new BigDecimal("50000.00"), 12, new BigDecimal("0.0700"), evalDate);

        FdComplianceService.RateDisclosureResponse response = complianceService.verifyRate(request, evalDate);

        assertEquals("NO_APPLICABLE_RATE", response.matchStatus());
        assertFalse(response.isMatch());
        assertNull(response.authoritativeRate());
    }

    @Test
    @DisplayName("verifyRate: Multiple overlapping rates returns AMBIGUOUS_RATE")
    void verifyRate_multipleRates_returnsAmbiguousRate() {
        LocalDate evalDate = LocalDate.of(2026, 6, 1);
        mockCandidateData(List.of(
                new MockCandidate(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 12, 31),
                        new BigDecimal("0.0750"),
                        "QUARTERLY",
                        "AT_MATURITY",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000000.00"),
                        1,
                        36
                ),
                new MockCandidate(
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 9, 30),
                        new BigDecimal("0.0780"),
                        "QUARTERLY",
                        "AT_MATURITY",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000000.00"),
                        1,
                        36
                )
        ));

        FdComplianceService.RateDisclosureRequest request = new FdComplianceService.RateDisclosureRequest(
                1L, "INR", new BigDecimal("50000.00"), 12, new BigDecimal("0.0750"), evalDate);

        FdComplianceService.RateDisclosureResponse response = complianceService.verifyRate(request, evalDate);

        assertEquals("AMBIGUOUS_RATE", response.matchStatus());
        assertFalse(response.isMatch());
        assertNull(response.authoritativeRate());
    }

    @Test
    @DisplayName("evaluateDate: Inclusive boundary check on start date is VALID")
    void evaluateDate_onStartDateBoundary_isValid() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        mockCandidateData(List.of(
                new MockCandidate(
                        startDate,
                        LocalDate.of(2026, 12, 31),
                        new BigDecimal("0.0750"),
                        "QUARTERLY",
                        "AT_MATURITY",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000000.00"),
                        1,
                        36
                )
        ));

        FdComplianceService.EffectiveDateRequest request = new FdComplianceService.EffectiveDateRequest(
                1L, "INR", new BigDecimal("10000.00"), 12, startDate);

        FdComplianceService.EffectiveDateResponse response = complianceService.evaluateDate(request);

        assertEquals("VALID", response.validityStatus());
        assertTrue(response.isValid());
        assertEquals(0, new BigDecimal("0.0750").compareTo(response.applicableRate()));
    }

    @Test
    @DisplayName("evaluateDate: Inclusive boundary check on end date is VALID")
    void evaluateDate_onEndDateBoundary_isValid() {
        LocalDate endDate = LocalDate.of(2026, 12, 31);
        mockCandidateData(List.of(
                new MockCandidate(
                        LocalDate.of(2026, 1, 1),
                        endDate,
                        new BigDecimal("0.0750"),
                        "QUARTERLY",
                        "AT_MATURITY",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000000.00"),
                        1,
                        36
                )
        ));

        FdComplianceService.EffectiveDateRequest request = new FdComplianceService.EffectiveDateRequest(
                1L, "INR", new BigDecimal("10000.00"), 12, endDate);

        FdComplianceService.EffectiveDateResponse response = complianceService.evaluateDate(request);

        assertEquals("VALID", response.validityStatus());
        assertTrue(response.isValid());
        assertEquals(0, new BigDecimal("0.0750").compareTo(response.applicableRate()));
    }

    @Test
    @DisplayName("evaluateDate: Evaluation date prior to effective start returns NOT_YET_EFFECTIVE")
    void evaluateDate_beforeEffectiveFrom_returnsNotYetEffective() {
        LocalDate evalDate = LocalDate.of(2025, 12, 31);
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        mockCandidateData(List.of(
                new MockCandidate(
                        startDate,
                        LocalDate.of(2026, 12, 31),
                        new BigDecimal("0.0750"),
                        "QUARTERLY",
                        "AT_MATURITY",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000000.00"),
                        1,
                        36
                )
        ));

        FdComplianceService.EffectiveDateRequest request = new FdComplianceService.EffectiveDateRequest(
                1L, "INR", new BigDecimal("10000.00"), 12, evalDate);

        FdComplianceService.EffectiveDateResponse response = complianceService.evaluateDate(request);

        assertEquals("NOT_YET_EFFECTIVE", response.validityStatus());
        assertFalse(response.isValid());
        assertEquals(startDate, response.effectiveFrom());
    }

    @Test
    @DisplayName("evaluateDate: Evaluation date after effective end returns EXPIRED")
    void evaluateDate_afterEffectiveTo_returnsExpired() {
        LocalDate evalDate = LocalDate.of(2027, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 12, 31);
        mockCandidateData(List.of(
                new MockCandidate(
                        LocalDate.of(2026, 1, 1),
                        endDate,
                        new BigDecimal("0.0750"),
                        "QUARTERLY",
                        "AT_MATURITY",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000000.00"),
                        1,
                        36
                )
        ));

        FdComplianceService.EffectiveDateRequest request = new FdComplianceService.EffectiveDateRequest(
                1L, "INR", new BigDecimal("10000.00"), 12, evalDate);

        FdComplianceService.EffectiveDateResponse response = complianceService.evaluateDate(request);

        assertEquals("EXPIRED", response.validityStatus());
        assertFalse(response.isValid());
        assertEquals(endDate, response.effectiveTo());
    }

    @Test
    @DisplayName("evaluateDate: Multiple overlapping active rate schedules returns AMBIGUOUS_CONFIGURATION")
    void evaluateDate_overlappingSchedules_returnsAmbiguousConfiguration() {
        LocalDate evalDate = LocalDate.of(2026, 6, 1);
        mockCandidateData(List.of(
                new MockCandidate(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 12, 31),
                        new BigDecimal("0.0750"),
                        "QUARTERLY",
                        "AT_MATURITY",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000000.00"),
                        1,
                        36
                ),
                new MockCandidate(
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 8, 31),
                        new BigDecimal("0.0800"),
                        "QUARTERLY",
                        "AT_MATURITY",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000000.00"),
                        1,
                        36
                )
        ));

        FdComplianceService.EffectiveDateRequest request = new FdComplianceService.EffectiveDateRequest(
                1L, "INR", new BigDecimal("10000.00"), 12, evalDate);

        FdComplianceService.EffectiveDateResponse response = complianceService.evaluateDate(request);

        assertEquals("AMBIGUOUS_CONFIGURATION", response.validityStatus());
        assertFalse(response.isValid());
        assertNull(response.applicableRate());
    }

    @Test
    @DisplayName("evaluateDate: Open-ended schedule (null from and to) is always VALID")
    void evaluateDate_openEndedSchedule_isValid() {
        LocalDate evalDate = LocalDate.of(2026, 6, 1);
        mockCandidateData(List.of(
                new MockCandidate(
                        null,
                        null,
                        new BigDecimal("0.0750"),
                        "QUARTERLY",
                        "AT_MATURITY",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000000.00"),
                        1,
                        36
                )
        ));

        FdComplianceService.EffectiveDateRequest request = new FdComplianceService.EffectiveDateRequest(
                1L, "INR", new BigDecimal("10000.00"), 12, evalDate);

        FdComplianceService.EffectiveDateResponse response = complianceService.evaluateDate(request);

        assertEquals("VALID", response.validityStatus());
        assertTrue(response.isValid());
        assertEquals(0, new BigDecimal("0.0750").compareTo(response.applicableRate()));
    }

    @Test
    @DisplayName("Activation: Valid current product schedule validates successfully")
    void validateProductScheduleForActivation_validCurrentSchedule_succeeds() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        when(jdbc.queryForList(contains("SELECT pro_product_id, pro_product_code"), eq(1L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pro_product_id", 1L, "pro_product_code", "FD_VALID", "pro_product_name", "Valid FD",
                        "pro_status", "DRAFT", "pro_effective_from", today.minusDays(5).toString(), "pro_effective_to", today.plusYears(1).toString()
                )));
        when(jdbc.queryForList(contains("FROM product_currency_config cfg"), eq(1L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pcc_currency_id", 1L, "cur_iso_code", "INR",
                        "pcc_minimum_amount", new BigDecimal("1000.00"), "pcc_maximum_amount", new BigDecimal("100000.00")
                )));
        mockCandidateData(List.of(new MockCandidate(
                today.minusDays(5), today.plusYears(1), new BigDecimal("0.0650"),
                "QUARTERLY", "AT_MATURITY", new BigDecimal("1000.00"), new BigDecimal("100000.00"), 1, 36
        )));

        assertDoesNotThrow(() -> complianceService.validateProductScheduleForActivation(1L));
    }

    @Test
    @DisplayName("Activation: Inverted date range (effectiveFrom > effectiveTo) throws 400 BAD_REQUEST")
    void validateProductScheduleForActivation_invertedDateRange_throwsBadRequest() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        when(jdbc.queryForList(contains("SELECT pro_product_id, pro_product_code"), eq(2L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pro_product_id", 2L, "pro_product_code", "FD_INVERT", "pro_product_name", "Inverted FD",
                        "pro_status", "DRAFT", "pro_effective_from", today.plusDays(10).toString(), "pro_effective_to", today.plusDays(2).toString()
                )));

        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> complianceService.validateProductScheduleForActivation(2L)
        );
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Effective-to date must be on or after"));
    }

    @Test
    @DisplayName("Activation: Expired schedule (effectiveTo < today) throws 400 BAD_REQUEST")
    void validateProductScheduleForActivation_expiredSchedule_throwsBadRequest() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        when(jdbc.queryForList(contains("SELECT pro_product_id, pro_product_code"), eq(3L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pro_product_id", 3L, "pro_product_code", "FD_EXPIRED", "pro_product_name", "Expired FD",
                        "pro_status", "DRAFT", "pro_effective_from", today.minusYears(2).toString(), "pro_effective_to", today.minusDays(1).toString()
                )));

        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> complianceService.validateProductScheduleForActivation(3L)
        );
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("expired"));
    }

    @Test
    @DisplayName("Activation: Valid future-dated schedule is evaluated on its future start date and succeeds")
    void validateProductScheduleForActivation_validFutureDatedSchedule_succeeds() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        LocalDate futureStart = today.plusMonths(1);
        LocalDate futureEnd = today.plusYears(2);
        when(jdbc.queryForList(contains("SELECT pro_product_id, pro_product_code"), eq(4L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pro_product_id", 4L, "pro_product_code", "FD_FUTURE", "pro_product_name", "Future FD",
                        "pro_status", "DRAFT", "pro_effective_from", futureStart.toString(), "pro_effective_to", futureEnd.toString()
                )));
        when(jdbc.queryForList(contains("FROM product_currency_config cfg"), eq(4L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pcc_currency_id", 1L, "cur_iso_code", "INR",
                        "pcc_minimum_amount", new BigDecimal("1000.00"), "pcc_maximum_amount", new BigDecimal("100000.00")
                )));
        mockCandidateData(List.of(new MockCandidate(
                futureStart, futureEnd, new BigDecimal("0.0700"),
                "QUARTERLY", "AT_MATURITY", new BigDecimal("1000.00"), new BigDecimal("100000.00"), 1, 24
        )));

        assertDoesNotThrow(() -> complianceService.validateProductScheduleForActivation(4L));
    }

    @Test
    @DisplayName("Activation: Overlapping rate schedules with differing rates on evaluation date throw 409 CONFLICT")
    void validateProductScheduleForActivation_ambiguousOverlappingSchedule_throwsConflict() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        when(jdbc.queryForList(contains("SELECT pro_product_id, pro_product_code"), eq(5L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pro_product_id", 5L, "pro_product_code", "FD_AMBIG", "pro_product_name", "Ambiguous FD",
                        "pro_status", "DRAFT", "pro_effective_from", today.minusDays(10).toString(), "pro_effective_to", today.plusYears(1).toString()
                )));
        when(jdbc.queryForList(contains("FROM product_currency_config cfg"), eq(5L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pcc_currency_id", 1L, "cur_iso_code", "INR",
                        "pcc_minimum_amount", new BigDecimal("1000.00"), "pcc_maximum_amount", new BigDecimal("100000.00")
                )));
        mockCandidateData(List.of(
                new MockCandidate(
                        today.minusDays(10), today.plusMonths(6), new BigDecimal("0.0650"),
                        "QUARTERLY", "AT_MATURITY", new BigDecimal("1000.00"), new BigDecimal("100000.00"), 1, 24
                ),
                new MockCandidate(
                        today.minusDays(5), today.plusMonths(3), new BigDecimal("0.0750"),
                        "QUARTERLY", "AT_MATURITY", new BigDecimal("1000.00"), new BigDecimal("100000.00"), 1, 24
                )
        ));

        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> complianceService.validateProductScheduleForActivation(5L)
        );
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("AMBIGUOUS_CONFIGURATION"));
    }

    @Test
    @DisplayName("Activation: Missing active currency configuration throws 400 BAD_REQUEST")
    void validateProductScheduleForActivation_missingCurrencyConfig_throwsBadRequest() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        when(jdbc.queryForList(contains("SELECT pro_product_id, pro_product_code"), eq(6L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pro_product_id", 6L, "pro_product_code", "FD_NO_CURR", "pro_product_name", "No Currency FD",
                        "pro_status", "DRAFT", "pro_effective_from", today.toString(), "pro_effective_to", today.plusYears(1).toString()
                )));
        when(jdbc.queryForList(contains("FROM product_currency_config cfg"), eq(6L)))
                .thenReturn(List.of());

        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> complianceService.validateProductScheduleForActivation(6L)
        );
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("requires at least one active currency configuration"));
    }

    @Test
    @DisplayName("updateProductSchedule: Inclusive date boundary overlap (Oct 28-Nov 7 vs Nov 7-Nov 28) throws 409 CONFLICT")
    void updateProductSchedule_inclusiveBoundaryOverlap_throwsConflict() {
        when(jdbc.queryForList(contains("SELECT pro_product_id, pro_product_code"), eq(10L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pro_product_id", 10L, "pro_product_code", "FD_BOUND", "pro_product_name", "Boundary Test FD",
                        "pro_status", "ACTIVE", "pro_event_version", 1L
                )));
        when(jdbc.query(contains("FROM currency WHERE"), any(ResultSetExtractor.class), eq("INR"))).thenReturn(1L);

        FdComplianceService.UpdateScheduleRequest request = new FdComplianceService.UpdateScheduleRequest(
                LocalDate.of(2026, 10, 28), LocalDate.of(2026, 12, 31),
                List.of(
                        new FdComplianceService.RateBandInput("INR", 12, 24, new BigDecimal("0.0700"),
                                "QUARTERLY", "AT_MATURITY", LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 7)),
                        new FdComplianceService.RateBandInput("INR", 12, 24, new BigDecimal("0.0750"),
                                "QUARTERLY", "AT_MATURITY", LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 28))
                )
        );

        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> complianceService.updateProductSchedule(10L, request)
        );
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("AMBIGUOUS_CONFIGURATION"));

        // Verify that database was NOT modified and outbox was NOT written
        verify(jdbc, never()).update(contains("DELETE FROM rule_condition"), anyLong());
        verify(jdbc, never()).update(contains("DELETE FROM product_rule"), anyLong());
        verify(jdbc, never()).update(contains("INSERT INTO outbox_event"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("updateProductSchedule: Sequential non-overlapping dates (Oct 28-Nov 7 vs Nov 8-Nov 28) succeed")
    void updateProductSchedule_sequentialNonOverlappingDates_succeeds() {
        when(jdbc.queryForList(contains("SELECT pro_product_id, pro_product_code"), eq(11L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pro_product_id", 11L, "pro_product_code", "FD_SEQ", "pro_product_name", "Sequential FD",
                        "pro_status", "DRAFT", "pro_event_version", 1L
                )));
        when(jdbc.query(contains("FROM currency WHERE"), any(ResultSetExtractor.class), eq("INR"))).thenReturn(1L);
        when(jdbc.query(contains("FROM product_currency_config WHERE"), any(ResultSetExtractor.class), eq(11L), eq(1L)))
                .thenReturn(java.util.Map.of("min", new BigDecimal("1000.00"), "max", new BigDecimal("1000000.00")));
        when(jdbc.query(contains("FROM rule_type WHERE"), any(ResultSetExtractor.class))).thenReturn(1L);
        when(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(101L, 102L);

        when(jdbc.queryForList(contains("FROM product_currency_config cfg"), eq(11L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pcc_currency_id", 1L, "cur_iso_code", "INR",
                        "pcc_minimum_amount", new BigDecimal("1000.00"), "pcc_maximum_amount", new BigDecimal("1000000.00")
                )));
        mockCandidateData(List.of(
                new MockCandidate(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 7), new BigDecimal("0.0700"),
                        "QUARTERLY", "AT_MATURITY", new BigDecimal("1000.00"), new BigDecimal("1000000.00"), 12, 24),
                new MockCandidate(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 28), new BigDecimal("0.0750"),
                        "QUARTERLY", "AT_MATURITY", new BigDecimal("1000.00"), new BigDecimal("1000000.00"), 12, 24)
        ));

        when(jdbc.query(contains("FROM product_currency_config pcc JOIN currency c"), any(RowMapper.class), eq(11L)))
                .thenReturn(List.of(new FdComplianceService.CurrencyLimitSummary("INR", new BigDecimal("1000.00"), new BigDecimal("1000000.00"), true)));
        when(jdbc.query(contains("SELECT r.pr_rule_id, c.cur_iso_code"), any(RowMapper.class), eq(11L)))
                .thenReturn(List.of());

        FdComplianceService.UpdateScheduleRequest request = new FdComplianceService.UpdateScheduleRequest(
                LocalDate.of(2026, 10, 28), LocalDate.of(2026, 12, 31),
                List.of(
                        new FdComplianceService.RateBandInput("INR", 12, 24, new BigDecimal("0.0700"),
                                "QUARTERLY", "AT_MATURITY", LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 7)),
                        new FdComplianceService.RateBandInput("INR", 12, 24, new BigDecimal("0.0750"),
                                "QUARTERLY", "AT_MATURITY", LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 28))
                )
        );

        assertDoesNotThrow(() -> complianceService.updateProductSchedule(11L, request));
        verify(jdbc, times(1)).update(contains("DELETE FROM product_rule"), eq(11L));
    }

    @Test
    @DisplayName("updateProductSchedule: Overlapping future-dated bands throw 409 CONFLICT")
    void updateProductSchedule_overlappingFutureDates_throwsConflict() {
        when(jdbc.queryForList(contains("SELECT pro_product_id, pro_product_code"), eq(12L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pro_product_id", 12L, "pro_product_code", "FD_FUT_CONF", "pro_product_name", "Future Conflict FD",
                        "pro_status", "DRAFT", "pro_event_version", 1L
                )));
        when(jdbc.query(contains("FROM currency WHERE"), any(ResultSetExtractor.class), eq("INR"))).thenReturn(1L);

        FdComplianceService.UpdateScheduleRequest request = new FdComplianceService.UpdateScheduleRequest(
                LocalDate.of(2026, 10, 28), LocalDate.of(2026, 12, 31),
                List.of(
                        new FdComplianceService.RateBandInput("INR", 12, 24, new BigDecimal("0.0701"),
                                "QUARTERLY", "AT_MATURITY", LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 28)),
                        new FdComplianceService.RateBandInput("INR", 12, 24, new BigDecimal("0.0800"),
                                "QUARTERLY", "AT_MATURITY", LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 28))
                )
        );

        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> complianceService.updateProductSchedule(12L, request)
        );
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("AMBIGUOUS_CONFIGURATION"));

        verify(jdbc, never()).update(contains("DELETE FROM product_rule"), anyLong());
    }

    @Test
    @DisplayName("updateProductSchedule: Different currencies with same dates and tenure do not conflict")
    void updateProductSchedule_differentCurrencies_doNotConflict() {
        when(jdbc.queryForList(contains("SELECT pro_product_id, pro_product_code"), eq(13L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pro_product_id", 13L, "pro_product_code", "FD_MULTI_CURR", "pro_product_name", "Multi Currency FD",
                        "pro_status", "DRAFT", "pro_event_version", 1L
                )));
        when(jdbc.query(contains("FROM currency WHERE"), any(ResultSetExtractor.class), eq("INR"))).thenReturn(1L);
        when(jdbc.query(contains("FROM currency WHERE"), any(ResultSetExtractor.class), eq("USD"))).thenReturn(2L);
        when(jdbc.query(contains("FROM product_currency_config WHERE"), any(ResultSetExtractor.class), eq(13L), eq(1L)))
                .thenReturn(java.util.Map.of("min", new BigDecimal("1000.00"), "max", new BigDecimal("1000000.00")));
        when(jdbc.query(contains("FROM product_currency_config WHERE"), any(ResultSetExtractor.class), eq(13L), eq(2L)))
                .thenReturn(java.util.Map.of("min", new BigDecimal("100.00"), "max", new BigDecimal("100000.00")));
        when(jdbc.query(contains("FROM rule_type WHERE"), any(ResultSetExtractor.class))).thenReturn(1L);
        when(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(101L, 102L);

        when(jdbc.queryForList(contains("FROM product_currency_config cfg"), eq(13L)))
                .thenReturn(List.of(
                        java.util.Map.of("pcc_currency_id", 1L, "cur_iso_code", "INR",
                                "pcc_minimum_amount", new BigDecimal("1000.00"), "pcc_maximum_amount", new BigDecimal("1000000.00")),
                        java.util.Map.of("pcc_currency_id", 2L, "cur_iso_code", "USD",
                                "pcc_minimum_amount", new BigDecimal("100.00"), "pcc_maximum_amount", new BigDecimal("100000.00"))
                ));
        mockCandidateData(List.of(
                new MockCandidate(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 12, 31), new BigDecimal("0.0700"),
                        "QUARTERLY", "AT_MATURITY", new BigDecimal("1000.00"), new BigDecimal("1000000.00"), 12, 24)
        ));

        when(jdbc.query(contains("FROM product_currency_config pcc JOIN currency c"), any(RowMapper.class), eq(13L)))
                .thenReturn(List.of());
        when(jdbc.query(contains("SELECT r.pr_rule_id, c.cur_iso_code"), any(RowMapper.class), eq(13L)))
                .thenReturn(List.of());

        FdComplianceService.UpdateScheduleRequest request = new FdComplianceService.UpdateScheduleRequest(
                LocalDate.of(2026, 10, 28), LocalDate.of(2026, 12, 31),
                List.of(
                        new FdComplianceService.RateBandInput("INR", 12, 24, new BigDecimal("0.0700"),
                                "QUARTERLY", "AT_MATURITY", LocalDate.of(2026, 10, 28), LocalDate.of(2026, 12, 31)),
                        new FdComplianceService.RateBandInput("USD", 12, 24, new BigDecimal("0.0500"),
                                "QUARTERLY", "AT_MATURITY", LocalDate.of(2026, 10, 28), LocalDate.of(2026, 12, 31))
                )
        );

        assertDoesNotThrow(() -> complianceService.updateProductSchedule(13L, request));
    }

    @Test
    @DisplayName("updateProductSchedule: Non-overlapping tenure ranges on same dates do not conflict")
    void updateProductSchedule_nonOverlappingTenure_doesNotConflict() {
        when(jdbc.queryForList(contains("SELECT pro_product_id, pro_product_code"), eq(14L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pro_product_id", 14L, "pro_product_code", "FD_DIFF_TENURE", "pro_product_name", "Diff Tenure FD",
                        "pro_status", "DRAFT", "pro_event_version", 1L
                )));
        when(jdbc.query(contains("FROM currency WHERE"), any(ResultSetExtractor.class), eq("INR"))).thenReturn(1L);
        when(jdbc.query(contains("FROM product_currency_config WHERE"), any(ResultSetExtractor.class), eq(14L), eq(1L)))
                .thenReturn(java.util.Map.of("min", new BigDecimal("1000.00"), "max", new BigDecimal("1000000.00")));
        when(jdbc.query(contains("FROM rule_type WHERE"), any(ResultSetExtractor.class))).thenReturn(1L);
        when(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(101L, 102L);

        when(jdbc.queryForList(contains("FROM product_currency_config cfg"), eq(14L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pcc_currency_id", 1L, "cur_iso_code", "INR",
                        "pcc_minimum_amount", new BigDecimal("1000.00"), "pcc_maximum_amount", new BigDecimal("1000000.00")
                )));
        mockCandidateData(List.of(
                new MockCandidate(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 12, 31), new BigDecimal("0.0650"),
                        "QUARTERLY", "AT_MATURITY", new BigDecimal("1000.00"), new BigDecimal("1000000.00"), 1, 12),
                new MockCandidate(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 12, 31), new BigDecimal("0.0750"),
                        "QUARTERLY", "AT_MATURITY", new BigDecimal("1000.00"), new BigDecimal("1000000.00"), 13, 24)
        ));

        when(jdbc.query(contains("FROM product_currency_config pcc JOIN currency c"), any(RowMapper.class), eq(14L)))
                .thenReturn(List.of());
        when(jdbc.query(contains("SELECT r.pr_rule_id, c.cur_iso_code"), any(RowMapper.class), eq(14L)))
                .thenReturn(List.of());

        FdComplianceService.UpdateScheduleRequest request = new FdComplianceService.UpdateScheduleRequest(
                LocalDate.of(2026, 10, 28), LocalDate.of(2026, 12, 31),
                List.of(
                        new FdComplianceService.RateBandInput("INR", 1, 12, new BigDecimal("0.0650"),
                                "QUARTERLY", "AT_MATURITY", LocalDate.of(2026, 10, 28), LocalDate.of(2026, 12, 31)),
                        new FdComplianceService.RateBandInput("INR", 13, 24, new BigDecimal("0.0750"),
                                "QUARTERLY", "AT_MATURITY", LocalDate.of(2026, 10, 28), LocalDate.of(2026, 12, 31))
                )
        );

        assertDoesNotThrow(() -> complianceService.updateProductSchedule(14L, request));
    }

    @Test
    @DisplayName("Activation: Future-dated interval overlap throws 409 CONFLICT")
    void validateProductScheduleForActivation_futureIntervalOverlap_throwsConflict() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        when(jdbc.queryForList(contains("SELECT pro_product_id, pro_product_code"), eq(15L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pro_product_id", 15L, "pro_product_code", "FD_FUT_ACT", "pro_product_name", "Future Act FD",
                        "pro_status", "DRAFT", "pro_effective_from", today.toString(), "pro_effective_to", today.plusYears(1).toString()
                )));
        when(jdbc.queryForList(contains("FROM product_currency_config cfg"), eq(15L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pcc_currency_id", 1L, "cur_iso_code", "INR",
                        "pcc_minimum_amount", new BigDecimal("1000.00"), "pcc_maximum_amount", new BigDecimal("100000.00")
                )));
        mockCandidateData(List.of(
                new MockCandidate(
                        today.plusDays(10), today.plusDays(30), new BigDecimal("0.0701"),
                        "QUARTERLY", "AT_MATURITY", new BigDecimal("1000.00"), new BigDecimal("100000.00"), 12, 24
                ),
                new MockCandidate(
                        today.plusDays(20), today.plusDays(40), new BigDecimal("0.0800"),
                        "QUARTERLY", "AT_MATURITY", new BigDecimal("1000.00"), new BigDecimal("100000.00"), 12, 24
                )
        ));

        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> complianceService.validateProductScheduleForActivation(15L)
        );
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("AMBIGUOUS_CONFIGURATION"));
    }

    @Test
    @DisplayName("Activation: Non-overlapping tenure bands on same dates succeed")
    void validateProductScheduleForActivation_nonOverlappingTenure_succeeds() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        when(jdbc.queryForList(contains("SELECT pro_product_id, pro_product_code"), eq(16L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pro_product_id", 16L, "pro_product_code", "FD_TEN_ACT", "pro_product_name", "Tenure Act FD",
                        "pro_status", "DRAFT", "pro_effective_from", today.toString(), "pro_effective_to", today.plusYears(1).toString()
                )));
        when(jdbc.queryForList(contains("FROM product_currency_config cfg"), eq(16L)))
                .thenReturn(List.of(java.util.Map.of(
                        "pcc_currency_id", 1L, "cur_iso_code", "INR",
                        "pcc_minimum_amount", new BigDecimal("1000.00"), "pcc_maximum_amount", new BigDecimal("100000.00")
                )));
        mockCandidateData(List.of(
                new MockCandidate(
                        today, today.plusYears(1), new BigDecimal("0.0650"),
                        "QUARTERLY", "AT_MATURITY", new BigDecimal("1000.00"), new BigDecimal("100000.00"), 1, 12
                ),
                new MockCandidate(
                        today, today.plusYears(1), new BigDecimal("0.0750"),
                        "QUARTERLY", "AT_MATURITY", new BigDecimal("1000.00"), new BigDecimal("100000.00"), 13, 24
                )
        ));

        assertDoesNotThrow(() -> complianceService.validateProductScheduleForActivation(16L));
    }

    @Test
    @DisplayName("dateIntervalsOverlap: Correctly detects inclusive boundary and open interval overlap")
    void dateIntervalsOverlap_truthTable() {
        // Oct 28 - Nov 7 vs Nov 7 - Nov 28 (inclusive boundary overlap)
        assertTrue(FdComplianceService.dateIntervalsOverlap(
                LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 7),
                LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 28)
        ));

        // Oct 28 - Nov 7 vs Nov 8 - Nov 28 (strictly sequential non-overlapping)
        assertFalse(FdComplianceService.dateIntervalsOverlap(
                LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 7),
                LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 28)
        ));

        // Open-ended dates (null)
        assertTrue(FdComplianceService.dateIntervalsOverlap(
                null, null,
                LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 7)
        ));
        assertTrue(FdComplianceService.dateIntervalsOverlap(
                LocalDate.of(2026, 10, 28), null,
                LocalDate.of(2026, 11, 7), LocalDate.of(2026, 12, 1)
        ));
        assertFalse(FdComplianceService.dateIntervalsOverlap(
                null, LocalDate.of(2026, 10, 27),
                LocalDate.of(2026, 10, 28), null
        ));
    }
}
