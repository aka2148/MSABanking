package com.bank.identitycustomer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {
    @Test void acceptsSignupStrengthForEveryPasswordChangePath() {
        assertTrue(PasswordPolicy.isStrong("OceanTrail49#Violet", "devsh"));
    }

    @Test void rejectsShortMissingCharacterTypesCommonAndUsernamePasswords() {
        assertFalse(PasswordPolicy.isStrong("Short9!Ab", "devsh"));
        assertFalse(PasswordPolicy.isStrong("alllowercasepassword1!", "devsh"));
        assertFalse(PasswordPolicy.isStrong("Password123!admin", "devsh"));
        assertFalse(PasswordPolicy.isStrong("DevShTrail49#Violet", "devsh"));
    }
}
