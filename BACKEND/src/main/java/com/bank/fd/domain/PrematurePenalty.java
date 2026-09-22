package com.bank.fd.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Pure premature-closure penalty policy for academic-v1 FDs.
 *
 * <p>The product's {@code CLOSURE_PENALTY} rule value is interpreted as a rate
 * reduction in percentage points: premature interest is computed with
 * {@link FdInterestCalculator#interestForPeriod} over elapsed days at
 * (contracted annual rate &minus; penalty points), floored at zero. Rates are
 * decimal fractions (0.0650 = 6.50% annually); no category boost is applied.
 * This class is Spring-free and never touches the legacy double-based
 * {@code FdCalculatorService}.</p>
 */
public final class PrematurePenalty {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO_SCALE_4 = new BigDecimal("0.0000");

    private PrematurePenalty() {
    }

    /**
     * Parse a {@code CLOSURE_PENALTY} rule value as percentage points.
     *
     * @param rule raw rule value, e.g. "1.00" or "1.00%"; may be null
     * @return penalty points, or {@link Optional#empty()} when the rule is
     *         null, blank, unparseable, or negative (fail-closed signal: the
     *         product cannot be closed prematurely)
     */
    public static Optional<BigDecimal> parsePenaltyPoints(String rule) {
        if (rule == null) {
            return Optional.empty();
        }
        String trimmed = rule.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        if (trimmed.endsWith("%")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
            if (trimmed.isEmpty()) {
                return Optional.empty();
            }
        }
        final BigDecimal points;
        try {
            points = new BigDecimal(trimmed);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        if (points.signum() < 0) {
            return Optional.empty();
        }
        return Optional.of(points);
    }

    /**
     * Contracted rate minus penalty points (points/100), floored at zero.
     *
     * @param contractedRate annual rate as decimal fraction, non-null and non-negative
     * @param penaltyPoints penalty in percentage points, non-null and non-negative
     * @return penalized rate, scale 4 HALF_UP, never negative
     */
    public static BigDecimal penalizedRate(BigDecimal contractedRate, BigDecimal penaltyPoints) {
        if (contractedRate == null || penaltyPoints == null) {
            throw new IllegalArgumentException("Contracted rate and penalty points must not be null");
        }
        if (contractedRate.signum() < 0 || penaltyPoints.signum() < 0) {
            throw new IllegalArgumentException("Contracted rate and penalty points must be non-negative");
        }
        BigDecimal penalized = contractedRate.subtract(
                penaltyPoints.divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP));
        if (penalized.signum() < 0) {
            return ZERO_SCALE_4;
        }
        return penalized.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Premature interest: {@link FdInterestCalculator#interestForPeriod} at the
     * penalized rate. Elapsed days &lt;= 0 yield 0.00 via the calculator.
     */
    public static BigDecimal prematureInterest(BigDecimal principal,
                                               BigDecimal contractedRate,
                                               BigDecimal penaltyPoints,
                                               LocalDate startInclusive,
                                               LocalDate endExclusive) {
        return FdInterestCalculator.interestForPeriod(
                principal, penalizedRate(contractedRate, penaltyPoints), startInclusive, endExclusive);
    }

    /**
     * Net premature settlement: principal + premature interest, scale 2 HALF_UP.
     */
    public static BigDecimal prematureSettlement(BigDecimal principal,
                                                 BigDecimal contractedRate,
                                                 BigDecimal penaltyPoints,
                                                 LocalDate startInclusive,
                                                 LocalDate endExclusive) {
        if (principal == null) {
            throw new IllegalArgumentException("Principal must not be null");
        }
        return principal.add(prematureInterest(
                principal, contractedRate, penaltyPoints, startInclusive, endExclusive))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Foregone interest: full interest minus penalized interest, floored at 0.00.
     */
    public static BigDecimal foregoneInterest(BigDecimal fullInterest, BigDecimal penalizedInterest) {
        if (fullInterest == null || penalizedInterest == null) {
            throw new IllegalArgumentException("Interest values must not be null");
        }
        BigDecimal difference = fullInterest.subtract(penalizedInterest)
                .setScale(2, RoundingMode.HALF_UP);
        if (difference.signum() < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return difference;
    }
}
