package com.bank.fd.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Quarterly-capitalizing Actual/365 math shared by booking estimates and daily batches. */
public final class FdCompoundCalculator {
    public static final String QUARTERLY = "QUARTERLY";
    private static final int INTERMEDIATE_SCALE = 12;
    private static final int ACCRUAL_SCALE = 8;

    private FdCompoundCalculator() { }

    /** One day's uncapitalized interest, retained at eight decimal places until a capitalization date. */
    public static BigDecimal dailyInterest(BigDecimal balance, BigDecimal annualRate) {
        if (balance == null || annualRate == null || balance.signum() < 0 || annualRate.signum() < 0) {
            throw new IllegalArgumentException("Balance and annual rate must be non-negative");
        }
        return balance.multiply(annualRate)
                .divide(BigDecimal.valueOf(365), INTERMEDIATE_SCALE, RoundingMode.HALF_UP)
                .setScale(ACCRUAL_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Computes an estimate using daily accrual and quarterly capitalization. The last partial quarter is
     * capitalized at the requested end date (normally maturity).
     */
    public static Result maturityAmount(BigDecimal principal, BigDecimal annualRate,
                                        LocalDate openDate, LocalDate endExclusive, int currencyScale) {
        if (principal == null || annualRate == null || openDate == null || endExclusive == null
                || currencyScale < 0) {
            throw new IllegalArgumentException("Principal, rate, dates, and currency scale are required");
        }
        if (principal.signum() < 0 || annualRate.signum() < 0 || endExclusive.isBefore(openDate)) {
            throw new IllegalArgumentException("Compound interest inputs are outside their valid range");
        }
        BigDecimal balance = principal;
        BigDecimal periodAccrued = BigDecimal.ZERO.setScale(ACCRUAL_SCALE, RoundingMode.HALF_UP);
        for (LocalDate accrualDate = openDate.plusDays(1);
             !accrualDate.isAfter(endExclusive); accrualDate = accrualDate.plusDays(1)) {
            periodAccrued = periodAccrued.add(dailyInterest(balance, annualRate));
            if (isCapitalizationDate(openDate, accrualDate, endExclusive)) {
                balance = balance.add(periodAccrued.setScale(currencyScale, RoundingMode.HALF_UP));
                periodAccrued = BigDecimal.ZERO.setScale(ACCRUAL_SCALE, RoundingMode.HALF_UP);
            }
        }
        balance = balance.setScale(currencyScale, RoundingMode.HALF_UP);
        return new Result(balance, balance.subtract(principal).setScale(currencyScale, RoundingMode.HALF_UP));
    }

    /** Calendar-quarter anniversaries anchored to the original open date, plus the final maturity date. */
    public static boolean isCapitalizationDate(LocalDate openDate, LocalDate accrualDate, LocalDate maturityDate) {
        if (accrualDate.equals(maturityDate)) return true;
        long months = ChronoUnit.MONTHS.between(openDate, accrualDate);
        for (long quarter = 3; quarter <= months + 3; quarter += 3) {
            if (openDate.plusMonths(quarter).equals(accrualDate)) return true;
        }
        return false;
    }

    public record Result(BigDecimal maturityAmount, BigDecimal interestAmount) { }
}
