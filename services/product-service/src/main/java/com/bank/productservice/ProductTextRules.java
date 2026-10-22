package com.bank.productservice;

final class ProductTextRules {
    private ProductTextRules() {}
    static String name(String value) { return value == null ? "" : value.trim().replaceAll(" +", " "); }
    static void requireIdentity(String name, String description) {
        if (!validName(name,255)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Enter a valid product name (3-255 characters including a letter).");
        if (!validDescription(description)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Enter 10-1000 characters including a letter, without markup or control characters.");
    }
    static String codePrefix(String value, String fallback, int limit) {
        String code = value == null ? "" : value.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        code = code.replaceFirst("^[^A-Z]*", "");
        if (code.isEmpty()) code = fallback;
        return code.substring(0, Math.min(limit, code.length())).replaceAll("_+$", "");
    }
    static boolean validCode(String value) { return value != null && value.matches("[A-Z][A-Z0-9_]{2,31}"); }
    static boolean validName(String value, int maximum) {
        String normalized = name(value);
        return normalized.length() >= 3 && normalized.length() <= maximum
                && normalized.matches(".*[\\p{L}].*") && normalized.matches("[\\p{L}0-9 .,&'()/\\-]+");
    }
    static boolean validDescription(String value) {
        if (value == null || value.isEmpty()) return true;
        String trimmed = value.trim();
        return trimmed.length() >= 10 && trimmed.length() <= 1000
                && trimmed.matches(".*\\p{L}.*") && !value.matches("(?s).*[<>\\p{Cc}].*");
    }
}
