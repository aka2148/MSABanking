package com.bank.security.service;

import com.bank.customer.entity.User;
import com.bank.customer.entity.UserCredential;
import com.bank.customer.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class LoginAttemptService {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;

    private final UserRepository userRepository;

    public LoginAttemptService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void recordFailure(String username) {
        userRepository.findByCredentialsUsername(username).ifPresent(user -> {
            UserCredential credentials = user.getCredentials();
            if (credentials == null || !user.isAccountNonLocked()) {
                return;
            }

            int failedAttempts = credentials.getFailedAttemptCount() + 1;
            credentials.setFailedAttemptCount(failedAttempts);
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                credentials.setLockedUntil(OffsetDateTime.now().plusMinutes(LOCK_MINUTES));
            }
            userRepository.save(user);
        });
    }

    @Transactional
    public void recordSuccess(String username, String remoteAddress) {
        userRepository.findByCredentialsUsername(username).ifPresent(user -> {
            UserCredential credentials = user.getCredentials();
            if (credentials == null) {
                return;
            }

            credentials.setFailedAttemptCount(0);
            credentials.setLockedUntil(null);
            credentials.setLastLoginAt(OffsetDateTime.now());
            credentials.setLastLoginIp(remoteAddress);
            userRepository.save(user);
        });
    }
}