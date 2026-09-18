package com.bank.fd.application;

import java.math.BigDecimal;

/** Immutable, server-resolved product/rule terms to snapshot on an FD. */
public record ProductTerms(
        Long productId,
        String productCode,
        String productName,
        String currencyCode,
        BigDecimal minimumAmount,
        BigDecimal maximumAmount,
        int minimumTenureMonths,
        int maximumTenureMonths,
        BigDecimal annualRate,
        String compoundingFrequency,
        String payoutFrequency,
        Long appliedRateOutcomeId,
        String closurePenaltyRule) {

    public ProductTerms {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product id must be positive");
        }
        requireText(productCode, "Product code");
        requireText(productName, "Product name");
        requireText(currencyCode, "Currency code");
        if (minimumAmount == null || minimumAmount.signum() <= 0
                || maximumAmount == null || maximumAmount.compareTo(minimumAmount) < 0) {
            throw new IllegalArgumentException("Product amount range is invalid");
        }
        if (minimumTenureMonths <= 0 || maximumTenureMonths < minimumTenureMonths) {
            throw new IllegalArgumentException("Product tenure range is invalid");
        }
        if (annualRate == null || annualRate.signum() < 0) {
            throw new IllegalArgumentException("Product annual rate must not be negative");
        }
        requireText(compoundingFrequency, "Compounding frequency");
        requireText(payoutFrequency, "Payout frequency");
        if (appliedRateOutcomeId == null || appliedRateOutcomeId <= 0) {
            throw new IllegalArgumentException("Applied rate outcome id must be positive");
        }
        requireText(closurePenaltyRule, "Closure penalty rule");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
