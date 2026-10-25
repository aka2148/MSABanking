package com.bank.fdservice;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class PrematureClosureCalculatorTest {
    @Test
    void usesFrozenRateAndPenaltyWithActual365AndCurrencyRounding() {
        var quote = PrematureClosureCalculator.calculate(new BigDecimal("10000.00"),
                new BigDecimal("0.0650"), new BigDecimal("1.0000"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 2), 2);

        assertEquals(182, quote.elapsedDays());
        assertEquals(new BigDecimal("0.055000000000"), quote.penalizedRate());
        assertEquals(new BigDecimal("274.25"), quote.paidInterest());
        assertEquals(new BigDecimal("324.11"), quote.fullInterest());
        assertEquals(new BigDecimal("49.86"), quote.penaltyAmount());
        assertEquals(new BigDecimal("10274.25"), quote.netPayable());
    }

    @Test
    void syntheticThousandDollarFixturePaysFiveInterestAndSettlesAtOneThousandFive() {
        var quote = PrematureClosureCalculator.calculate(new BigDecimal("1000.00"),
                new BigDecimal("0.0050"), BigDecimal.ZERO,
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1), 2);

        assertEquals(new BigDecimal("5.00"), quote.paidInterest());
        assertEquals(new BigDecimal("0.00"), quote.penaltyAmount());
        assertEquals(new BigDecimal("1005.00"), quote.netPayable());
    }

    @Test
    void rateNeverBecomesNegativeAndElapsedDaysNeverGoBelowZero() {
        var quote = PrematureClosureCalculator.calculate(new BigDecimal("100.00"),
                new BigDecimal("0.0050"), new BigDecimal("1.0000"),
                LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1), 2);

        assertEquals(0, quote.elapsedDays());
        assertEquals(BigDecimal.ZERO, quote.penalizedRate());
        assertEquals(new BigDecimal("100.00"), quote.netPayable());
    }

    @Test
    void rejectsInvalidCurrencyScaleAndPenaltyPoints() {
        assertThrows(IllegalArgumentException.class, () -> PrematureClosureCalculator.calculate(
                new BigDecimal("100"), new BigDecimal("0.05"), new BigDecimal("100.0001"),
                LocalDate.now(), LocalDate.now().plusDays(1), 2));
        assertThrows(IllegalArgumentException.class, () -> PrematureClosureCalculator.calculate(
                new BigDecimal("100"), new BigDecimal("0.05"), BigDecimal.ZERO,
                LocalDate.now(), LocalDate.now().plusDays(1), 9));
    }
}
