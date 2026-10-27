package com.bank.fdservice;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Wire contract, mirrored in the other service and pinned by the same golden vectors. */
final class MpinBinding {
    private MpinBinding() {}
    static final Map<String, List<String>> FIELDS = Map.of(
        "TRANSFER", List.of("fromAccountId", "toAccountNumber", "amount"),
        "FD_FUNDING", List.of("fdId", "principal"),
        "FD_CLOSE", List.of("fdId"),
        "FD_PREMATURE_REQUEST", List.of("fdId"),
        "LOAN_APPLICATION", List.of("productId", "amount", "currencyCode", "tenureMonths", "destinationSavingsAccountId"),
        "FOREX_REQUEST", List.of("productId", "amount", "fundingCurrencyCode", "destinationCurrencyCode")
    );
    static String canonical(String purpose, Object... values) {
        List<String> fields = FIELDS.get(purpose);
        if (fields == null || values.length != fields.size()) throw new IllegalArgumentException("Invalid MPIN binding fields");
        StringBuilder result = new StringBuilder(purpose);
        for (int i = 0; i < fields.size(); i++) {
            if (values[i] == null) throw new IllegalArgumentException("Missing MPIN binding field");
            String field = fields.get(i), value = values[i].toString().trim();
            if (field.equals("toAccountNumber") || field.endsWith("CurrencyCode") || field.equals("currencyCode")) {
                value = value.toUpperCase(Locale.ROOT);
            } else {
                BigDecimal number = new BigDecimal(value);
                value = field.endsWith("Id") || field.equals("tenureMonths")
                        ? number.toBigIntegerExact().toString()
                        : number.stripTrailingZeros().toPlainString();
            }
            result.append('|').append(value);
        }
        return result.toString();
    }
}
