package com.bank.security.userservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class UserServiceClientAdapter implements UserServiceClient {
    private final RestClient client;
    private final int maxRetries;
    private final int failureThreshold;
    private final long openDurationMs;
    private int consecutiveFailures;
    private long circuitOpenedAt;

    public UserServiceClientAdapter(
            RestClient userServiceRestClient,
            @Value("${app.user-service.max-retries:2}") int maxRetries,
            @Value("${app.user-service.failure-threshold:3}") int failureThreshold,
            @Value("${app.user-service.open-duration-ms:30000}") long openDurationMs) {
        this.client = userServiceRestClient;
        this.maxRetries = maxRetries;
        this.failureThreshold = failureThreshold;
        this.openDurationMs = openDurationMs;
    }

    @Override
    public CredentialValidation validateCredentials(String username, String password) {
        return execute(() -> client.post().uri("/internal/users/validate-credentials")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password))
                .retrieve().body(CredentialValidation.class));
    }

    @Override
    public AccountStatus getAccountStatus(String userId) {
        return execute(() -> client.get().uri("/internal/users/{id}/account-status", userId)
                .retrieve().body(AccountStatus.class));
    }

    @Override
    public void reportLoginAttempt(String userId, boolean successful, String ipAddress) {
        execute(() -> {
            client.post().uri("/internal/users/{id}/login-attempts", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("successful", successful, "ipAddress", ipAddress == null ? "unknown" : ipAddress))
                    .retrieve().toBodilessEntity();
            return null;
        });
    }

    @Override
    public List<String> getRoles(String userId) {
        return execute(() -> client.get().uri("/internal/users/{id}/roles", userId)
                .retrieve().body(RoleResponse.class)).roles();
    }

    @Override
    public Contacts getContacts(String userId) {
        return execute(() -> client.get().uri("/internal/users/{id}/contacts", userId)
                .retrieve().body(Contacts.class));
    }

    @Override
    public void unlock(String userId) {
        execute(() -> {
            client.post().uri("/internal/users/{id}/unlock", userId).retrieve().toBodilessEntity();
            return null;
        });
    }

    @Override
    public void changePassword(String userId, String currentPassword, String newPassword) {
        execute(() -> {
            client.post().uri("/internal/users/{id}/password/change", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("currentPassword", currentPassword, "newPassword", newPassword))
                    .retrieve().toBodilessEntity();
            return null;
        });
    }

    @Override
    public void requestPasswordReset(String username) {
        execute(() -> {
            client.post().uri("/internal/password/forgot")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", username))
                    .retrieve().toBodilessEntity();
            return null;
        });
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        execute(() -> {
            client.post().uri("/internal/password/reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("token", token, "newPassword", newPassword))
                    .retrieve().toBodilessEntity();
            return null;
        });
    }

    private synchronized <T> T execute(Supplier<T> operation) {
        long now = System.currentTimeMillis();
        if (circuitOpenedAt > 0 && now - circuitOpenedAt < openDurationMs) {
            throw new UserServiceUnavailableException();
        }

        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                T result = operation.get();
                consecutiveFailures = 0;
                circuitOpenedAt = 0;
                return result;
            } catch (RuntimeException ex) {
                lastFailure = ex;
            }
        }

        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            circuitOpenedAt = System.currentTimeMillis();
        }
        throw new UserServiceUnavailableException(lastFailure);
    }

    private record RoleResponse(List<String> roles) {}

    public static class UserServiceUnavailableException extends RuntimeException {
        public UserServiceUnavailableException() { super("User Service is unavailable"); }
        public UserServiceUnavailableException(Throwable cause) { super("User Service is unavailable", cause); }
    }
}