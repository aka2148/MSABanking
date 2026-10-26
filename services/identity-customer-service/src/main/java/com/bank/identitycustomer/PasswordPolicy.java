package com.bank.identitycustomer;

import java.util.List;
import java.util.Locale;

final class PasswordPolicy {
    static final String ERROR_MESSAGE = "Use a strong password that is different from your username.";
    private PasswordPolicy() {}

    static boolean isStrong(String password, String username) {
        if (password == null || password.length() < 12 || password.length() > 100) return false;
        String lower = password.toLowerCase(Locale.ROOT);
        String user = username == null ? "" : username.toLowerCase(Locale.ROOT);
        if (!user.isBlank() && lower.contains(user)) return false;
        if (!password.matches(".*[a-z].*") || !password.matches(".*[A-Z].*") || !password.matches(".*\\d.*")
                || !password.matches(".*[^A-Za-z0-9].*")) return false;
        return List.of("password", "qwerty", "letmein", "welcome", "admin", "iloveyou", "12345678")
                .stream().noneMatch(lower::contains);
    }
}
