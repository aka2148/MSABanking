package com.bank.identitycustomer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class CustomerRiskConfiguration {
    private final String defaultCategory;

    public CustomerRiskConfiguration(@Value("${app.customer.default-risk-category:LOW}") String configured) {
        String value = configured == null ? "" : configured.trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("LOW", "MEDIUM", "HIGH").contains(value)) {
            throw new IllegalArgumentException("app.customer.default-risk-category must be LOW, MEDIUM, or HIGH");
        }
        this.defaultCategory = value;
    }

    public String defaultCategory() { return defaultCategory; }
}
