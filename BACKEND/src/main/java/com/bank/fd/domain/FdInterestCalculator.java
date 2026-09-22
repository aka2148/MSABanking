package com.bank.fd.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Single shared Actual/365 simple-interest math for FD accrual and maturity.
 *
 * <p>Rate is a decimal fraction (0.0650 = 6.50% annually) per the academic-v1
 * policy. Percentage-scale inputs (6.50 for 6.50%) are rejected by contract
 * documentation only — this class performs no auto-detection heuristics.</p>
 *
 * <p>Intermediate division uses scale 12 with HALF_UP; final money values use
 * scale 2 with HALF_UP. {@link #maturityAmount} is numerically identical to
 * {@code AccountBookingService.maturityEstimate} for the same inputs.</p>
 */
public final class FdInterestCalculator {

    private FdInterestCalculator() {
    }

    /**
     * Interest accrued over [startInclusive, endExclusive) using Actual/365.
     *
     * @param principal principal amount, must be non-null and non-negative
     * @param annualRateDecimalFraction annual rate as decimal fraction, must be non-null and non-negative
     * @param startInclusive period start (inclusive), must be non-null
     * @param endExclusive period end (exclusive), must be non-null
     * @return interest scaled to 2 decimals with HALF_UP; ZERO (scale 2) when days &lt;= 0
     */
    public static BigDecimal interestForPeriod(BigDecimal principal,
                                               BigDecimal annualRateDecimalFraction,
                                               LocalDate startInclusive,
                                               LocalDate endExclusive) {
        if (principal == null || annualRateDecimalFraction == null) {
            throw new IllegalArgumentException("Principal and rate must not be null");
        }
        if (principal.signum() < 0 || annualRateDecimalFraction.signum() < 0) {
            throw new IllegalArgumentException("Principal and rate must be non-negative");
        }
        if (startInclusive == null || endExclusive == null) {
            throw new IllegalArgumentException("Period dates must not be null");
        }
        long days = ChronoUnit.DAYS.between(startInclusive, endExclusive);
        if (days <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return principal.multiply(annualRateDecimalFraction)
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(365), 12, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Maturity amount (principal + interest) for [openDate, maturityDate).
     *
     * @param principal principal amount, must be non-null and non-negative
     * @param annualRateDecimalFraction annual rate as decimal fraction, must be non-null and non-negative
     * @param openDate opening date (inclusive), must be non-null
     * @param maturityDate maturity date (exclusive), must be non-null
     * @return principal plus interest, scaled to 2 decimals with HALF_UP
     */
    public static BigDecimal maturityAmount(BigDecimal principal,
                                            BigDecimal annualRateDecimalFraction,
                                            LocalDate openDate,
                                            LocalDate maturityDate) {
        if (principal == null || annualRateDecimalFraction == null) {
            throw new IllegalArgumentException("Principal and rate must not be null");
        }
        return principal.add(interestForPeriod(principal, annualRateDecimalFraction, openDate, maturityDate))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
