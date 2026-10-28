package com.bank.identitycustomer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AuthDtoRedactionTest {
    @Test
    void authDtosDoNotPrintCredentialsCodesOrTokens() {
        String[] secrets = {"secret-password", "reset-token-value", "654321", "user@example.test", "mfa-secret"};
        Object[] values = {
                new PasswordRecoveryController.ForgotRequest("user@example.test"),
                new PasswordRecoveryController.VerifyRequest("user@example.test", "654321"),
                new PasswordRecoveryController.ResetRequest("reset-token-value", "secret-password"),
                new PasswordRecoveryService.VerifiedReset("reset-token-value", 600),
                new AuthController.RegisterRequest("user", "secret-password", "user@example.test", "Test User",
                        "1234567890", "1 Main St", "Test City", "Test State", "123456", "INDIVIDUAL"),
                new AuthController.LoginRequest("user@example.test", "secret-password"),
                new AuthController.ChangePasswordRequest("secret-password", "secret-password"),
                new AuthController.MfaLoginRequest("reset-token-value", "654321"),
                new AuthController.MfaCodeRequest("654321"),
                new AuthController.EmailOtpVerifyRequest("user@example.test", "654321"),
                new AuthController.MfaEnrollment("mfa-secret", "otpauth://secret"),
                new AuthController.JwtResponse(true, "reset-token-value", "Bearer", "user", "user@example.test",
                        List.of("ROLE_CUSTOMER"), false, "mfa-secret"),
                new PasswordResetController.ChangeRequest("secret-password", "secret-password")
        };

        for (Object value : values) {
            String rendered = value.toString();
            for (String secret : secrets) assertFalse(rendered.contains(secret), value.getClass().getSimpleName());
        }
    }
}
