package com.bank.security.userservice;

import java.util.List;

public interface UserServiceClient {
    CredentialValidation validateCredentials(String username, String password);
    AccountStatus getAccountStatus(String userId);
    void reportLoginAttempt(String userId, boolean successful, String ipAddress);
    List<String> getRoles(String userId);
    Contacts getContacts(String userId);
    void unlock(String userId);
    void changePassword(String userId, String currentPassword, String newPassword);
    void requestPasswordReset(String username);
    void resetPassword(String token, String newPassword);

    record CredentialValidation(boolean valid, String userId, String username, boolean mfaEnabled) {}
    record AccountStatus(boolean locked, boolean suspended, boolean mustChangePassword) {}
    record Contacts(String email, String phone) {}
}