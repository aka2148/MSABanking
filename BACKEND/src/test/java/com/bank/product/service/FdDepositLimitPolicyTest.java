package com.bank.product.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FdDepositLimitPolicyTest {
    @Test
    void selectsIncreasingLimitsForShortMediumAndLongInrTerms() {
        assertLimits("INR", 12, "5000", "10000000");
        assertLimits("INR", 36, "10000", "15000000");
        assertLimits("INR", 60, "25000", "20000000");
    }

    @Test
    void selectsTieredUsdAndKwdLimits() {
        assertLimits("USD", 12, "100", "1000000");
        assertLimits("USD", 24, "250", "1500000");
        assertLimits("USD", 48, "500", "2000000");
        assertLimits("KWD", 12, "50", "250000");
        assertLimits("KWD", 24, "100", "375000");
        assertLimits("KWD", 48, "250", "500000");
    }

    @Test
    void rejectsCurrencyWithoutAConfiguredPolicy() {
        assertThrows(IllegalArgumentException.class, () -> FdDepositLimitPolicy.forProduct("EUR", 12));
    }

    private static void assertLimits(String currency, int tenor, String minimum, String maximum) {
        var limits = FdDepositLimitPolicy.forProduct(currency, tenor);
        assertEquals(0, new BigDecimal(minimum).compareTo(limits.minimum()));
        assertEquals(0, new BigDecimal(maximum).compareTo(limits.maximum()));
        assertEquals(true, limits.maximum().compareTo(limits.minimum()) > 0);
    }
}
