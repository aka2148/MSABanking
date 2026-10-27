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
}
