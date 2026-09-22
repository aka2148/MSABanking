package com.bank.fd.domain;

import com.bank.fd.application.AccountBookingService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FdInterestCalculatorTest {

    @Test
    void zeroDayPeriodReturnsZero() {
        LocalDate day = LocalDate.of(2026, 3, 1);
        assertEquals(new BigDecimal("0.00"),
                FdInterestCalculator.interestForPeriod(new BigDecimal("100000"), new BigDecimal("0.0650"), day, day));
    }

    @Test
    void knownYearValueMatchesSimpleInterest() {
        BigDecimal interest = FdInterestCalculator.interestForPeriod(
                new BigDecimal("100000"), new BigDecimal("0.0650"),
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));
        assertEquals(new BigDecimal("6500.00"), interest);
        assertEquals(new BigDecimal("106500.00"),
                FdInterestCalculator.maturityAmount(
                        new BigDecimal("100000"), new BigDecimal("0.0650"),
                        LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1)));
    }

    @Test
    void oneDayAccrualRoundsHalfUpToTwoDecimals() {
        BigDecimal expected = new BigDecimal("100000").multiply(new BigDecimal("0.0650"))
                .multiply(BigDecimal.ONE)
                .divide(BigDecimal.valueOf(365), 12, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        assertEquals(expected,
                FdInterestCalculator.interestForPeriod(
                        new BigDecimal("100000"), new BigDecimal("0.0650"),
                        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2)));
    }

    @Test
    void rejectsNegativePrincipalAndNullRate() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 4, 1);
        assertThrows(IllegalArgumentException.class, () -> FdInterestCalculator.interestForPeriod(
                new BigDecimal("-1"), new BigDecimal("0.0650"), start, end));
        assertThrows(IllegalArgumentException.class, () -> FdInterestCalculator.interestForPeriod(
                new BigDecimal("1000"), null, start, end));
        assertThrows(IllegalArgumentException.class, () -> FdInterestCalculator.interestForPeriod(
                new BigDecimal("1000"), new BigDecimal("-0.01"), start, end));
        assertThrows(IllegalArgumentException.class, () -> FdInterestCalculator.interestForPeriod(
                null, new BigDecimal("0.0650"), start, end));
    }

    @Test
    void maturityAmountMatchesBookingEstimate() throws Exception {
        BigDecimal principal = new BigDecimal("50000");
        BigDecimal rate = new BigDecimal("0.0650");
        LocalDate open = LocalDate.of(2026, 1, 15);
        LocalDate maturity = open.plusMonths(12);
        Method estimate = AccountBookingService.class.getDeclaredMethod(
                "maturityEstimate", BigDecimal.class, BigDecimal.class, LocalDate.class, LocalDate.class);
        estimate.setAccessible(true);
        BigDecimal expected = (BigDecimal) estimate.invoke(null, principal, rate, open, maturity);
        assertEquals(expected, FdInterestCalculator.maturityAmount(principal, rate, open, maturity));
    }
}
