package com.bank.product.service;

import java.math.BigDecimal;
import java.util.Locale;

/** Academic demonstration deposit limits by FD tenor tier and supported currency. */
public final class FdDepositLimitPolicy {
    private FdDepositLimitPolicy() { }

    public static Limits forProduct(String currencyCode, int maximumTenureMonths) {
        String currency = currencyCode == null ? "" : currencyCode.trim().toUpperCase(Locale.ROOT);
        int tier = maximumTenureMonths <= 12 ? 1 : maximumTenureMonths <= 36 ? 2 : 3;
        return switch (currency) {
            case "INR" -> switch (tier) {
                case 1 -> new Limits("5000", "10000000");
                case 2 -> new Limits("10000", "15000000");
                default -> new Limits("25000", "20000000");
            };
            case "USD" -> switch (tier) {
                case 1 -> new Limits("100", "1000000");
                case 2 -> new Limits("250", "1500000");
                default -> new Limits("500", "2000000");
            };
            case "KWD" -> switch (tier) {
                case 1 -> new Limits("50", "250000");
                case 2 -> new Limits("100", "375000");
                default -> new Limits("250", "500000");
            };
            default -> throw new IllegalArgumentException("Unsupported FD currency: " + currencyCode);
        };
    }

    public record Limits(BigDecimal minimum, BigDecimal maximum) {
        private Limits(String minimum, String maximum) {
            this(new BigDecimal(minimum), new BigDecimal(maximum));
        }
    }
}
