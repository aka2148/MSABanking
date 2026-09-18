package com.bank.fd.application;

import java.math.BigDecimal;

/** Requested product terms used to resolve server-owned normalized references. */
public record ProductTermsQuery(
        Long productId,
        BigDecimal principal,
        int tenureMonths,
        String currencyCode) {

    public ProductTermsQuery {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product id must be positive");
        }
        if (principal == null || principal.signum() <= 0) {
            throw new IllegalArgumentException("Principal must be positive");
        }
        if (tenureMonths <= 0) {
            throw new IllegalArgumentException("Tenure must be positive");
        }
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("Currency code must not be blank");
        }
    }
}
