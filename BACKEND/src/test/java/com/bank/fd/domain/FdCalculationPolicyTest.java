package com.bank.fd.domain;

import com.bank.fd.application.FdActorContext;
import com.bank.fd.application.ProductTerms;
import com.bank.fd.application.ProductTermsPort;
import com.bank.fd.application.ProductTermsQuery;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FdCalculationPolicyTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-03T23:30:00Z");

    @Test
    void rejectsMissingOrInvalidPolicyValues() {
        assertThrows(IllegalArgumentException.class, () -> policy(null));
        assertThrows(IllegalArgumentException.class, () -> policy(""));
        assertThrows(IllegalArgumentException.class, () -> new FdCalculationPolicy(
                "academic-v1", null, "ACTUAL_365", 2, 2,
                RoundingMode.HALF_UP, true, false));
        assertThrows(IllegalArgumentException.class, () -> new FdCalculationPolicy(
                "academic-v1", "DECIMAL_FRACTION", "", 2, 2,
                RoundingMode.HALF_UP, true, false));
        assertThrows(IllegalArgumentException.class, () -> new FdCalculationPolicy(
                "academic-v1", "DECIMAL_FRACTION", "ACTUAL_365", 0, 2,
                RoundingMode.HALF_UP, true, false));
        assertThrows(IllegalArgumentException.class, () -> new FdCalculationPolicy(
                "academic-v1", "DECIMAL_FRACTION", "ACTUAL_365", 2, 2,
                null, true, false));
    }

    @Test
    void policyRoundTripsTheAcademicFinancialContract() {
        FdCalculationPolicy policy = policy("academic-v1");

        assertEquals("academic-v1", policy.version());
        assertEquals(FdCalculationPolicy.RateUnit.DECIMAL_FRACTION, policy.rateUnit());
        assertEquals(FdCalculationPolicy.DayCountConvention.ACTUAL_365, policy.dayCountConvention());
        assertEquals(2, policy.currencyScale());
        assertEquals(2, policy.displayScale());
        assertEquals(RoundingMode.HALF_UP, policy.roundingMode());
        assertTrue(policy.openingDateIncluded());
        assertTrue(!policy.maturityDateIncluded());
    }

    @Test
    void businessDateUsesInjectedClockAndZone() {
        FdTimePolicy policy = new FdTimePolicy(
                Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC")), ZoneId.of("America/New_York"));

        assertEquals(FIXED_INSTANT, policy.now());
        assertEquals(LocalDate.of(2026, 2, 3), policy.businessDate());
    }

    @Test
    void contractsAreTypedAndDoNotRequirePersistenceOrSecurityContext() {
        FdActorContext actor = new FdActorContext("local-user-7", "CUSTOMER", Set.of("CUSTOMER"), true);
        ProductTermsQuery query = new ProductTermsQuery(
                7L, new BigDecimal("1000.00"), 12, "USD");
        ProductTerms terms = new ProductTerms(
                7L, "FD-12", "Academic FD", "USD",
                new BigDecimal("100.00"), new BigDecimal("100000.00"),
                1, 24, new BigDecimal("0.0650"), "SIMPLE", "MATURITY",
                11L, "STANDARD_PENALTY");
        ProductTermsPort port = (ignored, ignoredDate) -> terms;

        assertEquals("local-user-7", actor.actorId());
        assertTrue(actor.roles().contains("CUSTOMER"));
        assertEquals(terms, port.resolveActiveTerms(query, LocalDate.of(2026, 2, 3)));
    }

    private FdCalculationPolicy policy(String version) {
        return new FdCalculationPolicy(
                version, "DECIMAL_FRACTION", "ACTUAL_365", 2, 2,
                RoundingMode.HALF_UP, true, false);
    }
}
