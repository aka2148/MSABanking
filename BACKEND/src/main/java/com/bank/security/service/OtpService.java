package com.bank.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

    public Challenge send(String userId, String username, String destination, String purpose) {
        String challengeId = UUID.randomUUID().toString();
        String code = "%06d".formatted(random.nextInt(1_000_000));
        challenges.put(challengeId, new Challenge(challengeId, userId, username, code,
                Instant.now().plusSeconds(300), purpose, false));
        log.info("OTP created for user {} via {} for {}", userId, destination, purpose);
        return challenges.get(challengeId);
    }

    public Challenge verify(String challengeId, String code) {
        Challenge challenge = challenges.get(challengeId);
        if (challenge == null || challenge.consumed() || challenge.expiresAt().isBefore(Instant.now())
                || !challenge.code().equals(code)) {
            return null;
        }
        Challenge consumed = new Challenge(challenge.challengeId(), challenge.userId(), challenge.username(),
                challenge.code(), challenge.expiresAt(), challenge.purpose(), true);
        challenges.put(challengeId, consumed);
        return consumed;
    }

    public record Challenge(String challengeId, String userId, String username, String code,
                            Instant expiresAt, String purpose, boolean consumed) {}
}