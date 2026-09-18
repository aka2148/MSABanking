package com.bank.fd.domain;

import java.math.RoundingMode;
import java.util.Locale;

/**
 * Immutable financial convention for the academic fixed-deposit workflow.
 *
 * <p>The policy is deliberately a small versioned contract, not a general
 * production rule engine. Existing accounts must retain the version used at
 * booking time when later calculation services consume this contract.</p>
 */
public final class FdCalculationPolicy {

    public enum RateUnit {
        DECIMAL_FRACTION
    }

    public enum DayCountConvention {
        ACTUAL_365
    }

    private final String version;
    private final RateUnit rateUnit;
    private final DayCountConvention dayCountConvention;
    private final int currencyScale;
    private final int displayScale;
    private final RoundingMode roundingMode;
    private final boolean openingDateIncluded;
    private final boolean maturityDateIncluded;

    public FdCalculationPolicy(
            String version,
            String rateUnit,
            String dayCountConvention,
            int currencyScale,
            int displayScale,
            RoundingMode roundingMode,
            boolean openingDateIncluded,
            boolean maturityDateIncluded) {
        this(version, parseRateUnit(rateUnit), parseDayCountConvention(dayCountConvention),
                currencyScale, displayScale, roundingMode, openingDateIncluded, maturityDateIncluded);
    }

    public FdCalculationPolicy(
            String version,
            RateUnit rateUnit,
            DayCountConvention dayCountConvention,
            int currencyScale,
            int displayScale,
            RoundingMode roundingMode,
            boolean openingDateIncluded,
            boolean maturityDateIncluded) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("FD calculation policy version must not be blank");
        }
        this.version = version;
        if (rateUnit == null) {
            throw new IllegalArgumentException("rateUnit must not be null");
        }
        if (dayCountConvention == null) {
            throw new IllegalArgumentException("dayCountConvention must not be null");
        }
        this.rateUnit = rateUnit;
        this.dayCountConvention = dayCountConvention;
        if (currencyScale <= 0 || displayScale <= 0) {
            throw new IllegalArgumentException("FD policy scales must be positive");
        }
        this.currencyScale = currencyScale;
        this.displayScale = displayScale;
        if (roundingMode == null) {
            throw new IllegalArgumentException("roundingMode must not be null");
        }
        this.roundingMode = roundingMode;
        this.openingDateIncluded = openingDateIncluded;
        this.maturityDateIncluded = maturityDateIncluded;
    }

    public String version() {
        return version;
    }

    public RateUnit rateUnit() {
        return rateUnit;
    }

    public DayCountConvention dayCountConvention() {
        return dayCountConvention;
    }

    public int currencyScale() {
        return currencyScale;
    }

    public int displayScale() {
        return displayScale;
    }

    public RoundingMode roundingMode() {
        return roundingMode;
    }

    public boolean openingDateIncluded() {
        return openingDateIncluded;
    }

    public boolean maturityDateIncluded() {
        return maturityDateIncluded;
    }

    private static RateUnit parseRateUnit(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("FD policy rate unit must not be blank");
        }
        try {
            return RateUnit.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported FD policy rate unit: " + value, exception);
        }
    }

    private static DayCountConvention parseDayCountConvention(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("FD policy day-count convention must not be blank");
        }
        try {
            return DayCountConvention.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unsupported FD policy day-count convention: " + value, exception);
        }
    }
}
