package com.bank.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class OtpService {
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    @Value("${app.mail.from:noreply@bankinglab.local}")
    private String mailFrom;
    private final JavaMailSender mailSender;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> failedAttempts = new ConcurrentHashMap<>();

    public OtpService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public Challenge send(String userId, String username, String destination, String purpose) {
        return createChallenge(userId, username, purpose);
    }

    public Challenge sendEmail(String userId, String username, String destination, String purpose) {
        if (destination == null || destination.isBlank() || !destination.contains("@")) {
            throw new com.bank.common.exception.InvalidInputException("A registered email address is required for sign-in verification");
        }
        String challengeId = UUID.randomUUID().toString();
        String code = "%06d".formatted(random.nextInt(1_000_000));
        Challenge challenge = new Challenge(challengeId, userId, username, code,
                Instant.now().plusSeconds(300), purpose, false);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(destination);
        message.setSubject("Your Banking Lab sign-in code");
        message.setText("Your sign-in verification code is " + code + ". It expires in 5 minutes. If you did not request it, ignore this message.");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new OtpDeliveryException();
        }
        challenges.put(challengeId, challenge);
        log.info("Email verification challenge sent for user {}", userId);
        return challenge;
    }

    private Challenge createChallenge(String userId, String username, String purpose) {
        String challengeId = UUID.randomUUID().toString();
        String code = "%06d".formatted(random.nextInt(1_000_000));
        Challenge challenge = new Challenge(challengeId, userId, username, code,
                Instant.now().plusSeconds(300), purpose, false);
        challenges.put(challengeId, challenge);
        log.info("OTP created for user {} for {}", userId, purpose);
        return challenge;
    }

    public Challenge verify(String challengeId, String code) {
        Challenge challenge = challenges.get(challengeId);
        if (challenge == null || challenge.consumed()) {
            return null;
        }
        if (challenge.expiresAt().isBefore(Instant.now())) {
            challenges.remove(challengeId);
            failedAttempts.remove(challengeId);
            return null;
        }
        if (code == null || !java.security.MessageDigest.isEqual(
                challenge.code().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                code.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            int attempts = failedAttempts.computeIfAbsent(challengeId, ignored -> new AtomicInteger()).incrementAndGet();
            if (attempts >= 5) { challenges.remove(challengeId); failedAttempts.remove(challengeId); }
            return null;
        }
        challenges.remove(challengeId);
        failedAttempts.remove(challengeId);
        return new Challenge(challenge.challengeId(), challenge.userId(), challenge.username(),
                challenge.code(), challenge.expiresAt(), challenge.purpose(), true);
    }

    public record Challenge(String challengeId, String userId, String username, String code,
                            Instant expiresAt, String purpose, boolean consumed) {}

    public static class OtpDeliveryException extends RuntimeException {
        public OtpDeliveryException() { super("Email verification could not be delivered"); }
    }
}
