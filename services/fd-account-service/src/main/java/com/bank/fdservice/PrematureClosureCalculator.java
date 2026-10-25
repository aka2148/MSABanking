package com.bank.fdservice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Deterministic Actual/365 quote calculation using the terms frozen when the FD was booked. */
public final class PrematureClosureCalculator {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");

    private PrematureClosureCalculator() { }

    public static Quote calculate(BigDecimal principal, BigDecimal annualRate, BigDecimal penaltyPoints,
                                   LocalDate opened, LocalDate throughDate, int currencyScale) {
        if (principal == null || principal.signum() <= 0 || annualRate == null || annualRate.signum() < 0
                || penaltyPoints == null || penaltyPoints.signum() < 0 || penaltyPoints.compareTo(ONE_HUNDRED) > 0
                || opened == null || throughDate == null || currencyScale < 0 || currencyScale > 8) {
            throw new IllegalArgumentException("Frozen FD terms and currency scale are invalid");
        }

        LocalDate end = throughDate.isBefore(opened) ? opened : throughDate;
        long elapsedDays = ChronoUnit.DAYS.between(opened, end);
        BigDecimal penalizedRate = annualRate.subtract(
                penaltyPoints.divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP)).max(BigDecimal.ZERO);
        BigDecimal paidInterest = interest(principal, penalizedRate, elapsedDays, currencyScale);
        BigDecimal fullInterest = interest(principal, annualRate, elapsedDays, currencyScale);
        BigDecimal penaltyAmount = fullInterest.subtract(paidInterest).max(BigDecimal.ZERO)
                .setScale(currencyScale, RoundingMode.HALF_UP);
        BigDecimal netPayable = principal.add(paidInterest).setScale(currencyScale, RoundingMode.HALF_UP);
        return new Quote(elapsedDays, annualRate, penaltyPoints, penalizedRate, paidInterest,
                fullInterest, penaltyAmount, netPayable);
    }

    private static BigDecimal interest(BigDecimal principal, BigDecimal rate, long days, int scale) {
        return principal.multiply(rate).multiply(BigDecimal.valueOf(days))
                .divide(DAYS_PER_YEAR, 16, RoundingMode.HALF_UP)
                .setScale(scale, RoundingMode.HALF_UP);
    }

    public record Quote(long elapsedDays, BigDecimal annualRate, BigDecimal penaltyPoints,
                        BigDecimal penalizedRate, BigDecimal paidInterest, BigDecimal fullInterest,
                        BigDecimal penaltyAmount, BigDecimal netPayable) { }
}
