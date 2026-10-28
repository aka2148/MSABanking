package com.bank.identitycustomer;

import io.jsonwebtoken.io.Decoders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class PasswordRecoveryService {
    public static final String ACCEPTED_MESSAGE = "If the account is eligible, a verification code has been sent to its registered email.";
    public static final String INVALID_CODE_MESSAGE = "The code is incorrect or has expired.";
    public static final String INVALID_TOKEN_MESSAGE = "The reset token is invalid or has expired.";
    public static final String UNAVAILABLE_MESSAGE = "Password reset is not available right now. Please try again later.";
    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryService.class);
    private static final byte[] HMAC_DOMAIN = "moneypal/password-reset-otp/v1".getBytes(StandardCharsets.UTF_8);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final AuthController authController;
    private final PasswordResetMailDispatcher mailDispatcher;
    private final boolean enabled;
    private final String mailApiKey;
    private final byte[] otpHmacKey;
    private final SecureRandom random = new SecureRandom();

    PasswordRecoveryService(JdbcTemplate jdbc, PasswordEncoder passwords, AuthController authController,
            PasswordResetMailDispatcher mailDispatcher,
            @Value("${app.auth.password-reset-enabled:true}") boolean enabled,
            @Value("${app.auth.password-reset-api-key:}") String mailApiKey,
            @Value("${app.jwt.secret}") String jwtSecret) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.authController = authController;
        this.mailDispatcher = mailDispatcher;
        this.enabled = enabled;
        this.mailApiKey = mailApiKey;
        this.otpHmacKey = deriveOtpKey(Decoders.BASE64.decode(jwtSecret));
    }

    @Transactional
    public Accepted forgot(String identifier) {
        requireAvailable();
        jdbc.update("DELETE FROM password_reset_otps WHERE pro_created_at < DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 24 HOUR)");

        List<Map<String, Object>> users = LoginIdentifierResolver.resolve(jdbc, identifier, true);
        if (users.isEmpty()) return new Accepted(ACCEPTED_MESSAGE);
        Map<String, Object> user = users.get(0);
        long userId = ((Number) user.get("usr_user_id")).longValue();
        if (!eligible(userId, user)) return new Accepted(ACCEPTED_MESSAGE);

        String email = verifiedPrimaryEmail(userId);
        if (email == null) return new Accepted(ACCEPTED_MESSAGE);

        Integer blocked = jdbc.queryForObject("SELECT CASE WHEN COUNT(*)>=5 OR "
                + "MAX(pro_sent_at)>DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 60 SECOND) THEN 1 ELSE 0 END "
                + "FROM password_reset_otps WHERE pro_user_id=? "
                + "AND pro_sent_at>=DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 HOUR)", Integer.class, userId);
        if (blocked != null && blocked == 1) return new Accepted(ACCEPTED_MESSAGE);

        jdbc.update("UPDATE password_reset_otps SET pro_consumed_at=UTC_TIMESTAMP(6) "
                + "WHERE pro_user_id=? AND pro_consumed_at IS NULL", userId);
        String code = String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000));
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO password_reset_otps "
                    + "(pro_user_id,pro_code_hmac,pro_expires_at,pro_sent_at) "
                    + "VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 10 MINUTE),UTC_TIMESTAMP(6))",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setString(2, hmac(code));
            return statement;
        }, key);
        long otpId = key.getKey().longValue();
        afterCommit(() -> mailDispatcher.sendCode(otpId, email, code));
        return new Accepted(ACCEPTED_MESSAGE);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public VerifiedReset verify(String identifier, String code) {
        requireAvailable();
        List<Map<String, Object>> users = LoginIdentifierResolver.resolve(jdbc, identifier, true);
        Map<String, Object> user = users.isEmpty() ? null : users.get(0);
        long userId = user == null ? -1L : ((Number) user.get("usr_user_id")).longValue();
        Map<String, Object> otp = null;
        if (user != null && eligible(userId, user) && verifiedPrimaryEmail(userId) != null) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT pro_id,pro_code_hmac,pro_attempts "
                    + "FROM password_reset_otps WHERE pro_user_id=? AND pro_consumed_at IS NULL "
                    + "AND pro_expires_at>UTC_TIMESTAMP(6) ORDER BY pro_created_at DESC LIMIT 1 FOR UPDATE", userId);
            if (!rows.isEmpty()) otp = rows.get(0);
        }

        String expected = otp == null ? hmac("000000") : (String) otp.get("pro_code_hmac");
        boolean matches = MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                hmac(code).getBytes(StandardCharsets.US_ASCII));
        if (otp == null || !matches || ((Number) otp.get("pro_attempts")).intValue() >= 5) {
            if (otp != null && ((Number) otp.get("pro_attempts")).intValue() < 5) {
                jdbc.update("UPDATE password_reset_otps SET "
                        + "pro_consumed_at=CASE WHEN pro_attempts>=4 THEN UTC_TIMESTAMP(6) ELSE pro_consumed_at END, "
                        + "pro_attempts=pro_attempts+1 "
                        + "WHERE pro_id=? AND pro_consumed_at IS NULL", otp.get("pro_id"));
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_CODE_MESSAGE);
        }

        int consumed = jdbc.update("UPDATE password_reset_otps SET pro_consumed_at=UTC_TIMESTAMP(6) "
                + "WHERE pro_id=? AND pro_consumed_at IS NULL AND pro_expires_at>UTC_TIMESTAMP(6) AND pro_attempts<5",
                otp.get("pro_id"));
        if (consumed != 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_CODE_MESSAGE);

        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String resetToken = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        jdbc.update("DELETE FROM password_reset_tokens WHERE prt_user_id=?", userId);
        jdbc.update("INSERT INTO password_reset_tokens(prt_user_id,prt_token_hash,prt_expires_at) "
                + "VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 10 MINUTE))", userId, sha256(resetToken));
        return new VerifiedReset(resetToken, 600);
    }

    @Transactional
    public AuthController.JwtResponse reset(String resetToken, String newPassword) {
        requireAvailable();
        String tokenHash = sha256(resetToken);
        int consumed = jdbc.update("UPDATE password_reset_tokens SET prt_used_at=UTC_TIMESTAMP(6) "
                + "WHERE prt_token_hash=? AND prt_used_at IS NULL AND prt_expires_at>UTC_TIMESTAMP(6)", tokenHash);
        if (consumed != 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_TOKEN_MESSAGE);

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT t.prt_user_id,c.ucr_username,c.ucr_password_hash,"
                + "u.usr_status FROM password_reset_tokens t JOIN user_credentials c ON c.ucr_user_id=t.prt_user_id "
                + "JOIN users u ON u.usr_user_id=t.prt_user_id WHERE t.prt_token_hash=? FOR UPDATE", tokenHash);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_TOKEN_MESSAGE);
        Map<String, Object> row = rows.get(0);
        long userId = ((Number) row.get("prt_user_id")).longValue();
        String username = (String) row.get("ucr_username");
        String currentHash = (String) row.get("ucr_password_hash");
        if (!eligible(userId, row)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_TOKEN_MESSAGE);
        if (!PasswordPolicy.isStrong(newPassword, username))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PasswordPolicy.ERROR_MESSAGE);
        if (passwords.matches(newPassword, currentHash) || jdbc.queryForList(
                "SELECT uph_password_hash FROM user_password_history WHERE uph_user_id=? "
                        + "ORDER BY uph_changed_at DESC LIMIT 5", String.class, userId)
                .stream().anyMatch(old -> passwords.matches(newPassword, old))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a password you have not used before");
        }

        String email = verifiedPrimaryEmail(userId);
        jdbc.update("INSERT INTO user_password_history(uph_user_id,uph_password_hash) VALUES(?,?)", userId, currentHash);
        jdbc.update("UPDATE user_credentials SET ucr_password_hash=?,ucr_password_changed_at=UTC_TIMESTAMP(6),"
                + "ucr_must_change_password=0,ucr_failed_attempt_count=0,ucr_locked_until=NULL,"
                + "ucr_token_version=ucr_token_version+1,ucr_last_login_at=UTC_TIMESTAMP(6) WHERE ucr_user_id=?",
                passwords.encode(newPassword), userId);
        jdbc.update("DELETE FROM password_reset_tokens WHERE prt_user_id=?", userId);
        jdbc.update("DELETE FROM password_reset_otps WHERE pro_user_id=?", userId);

        AuthController.JwtResponse session = authController.passwordResetSession(userId);
        afterCommit(() -> {
            log.info("Password reset completed for userId={}", userId);
            if (email != null) mailDispatcher.sendPasswordChanged(email);
        });
        return session;
    }

    private boolean eligible(long userId, Map<String, Object> user) {
        if (!"ACTIVE".equals(user.get("usr_status"))) return false;
        List<String> roles = jdbc.queryForList("SELECT r.rol_role_code FROM roles r "
                + "JOIN user_roles ur ON ur.ur_role_id=r.rol_role_id WHERE ur.ur_user_id=?", String.class, userId);
        return roles.contains("ROLE_CUSTOMER") && !roles.contains("ROLE_ADMIN")
                && !roles.contains("ROLE_BANK_OFFICER");
    }

    private String verifiedPrimaryEmail(long userId) {
        return jdbc.query("SELECT e.cem_email_address FROM customer_emails e "
                        + "JOIN customers c ON c.cus_customer_id=e.cem_customer_id "
                        + "WHERE c.cus_user_id=? AND e.cem_is_primary=1 AND e.cem_is_verified=1 "
                        + "ORDER BY e.cem_email_id LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, userId);
    }

    private void requireAvailable() {
        if (!enabled || mailApiKey == null || mailApiKey.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(otpHmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to verify reset code");
        }
    }

    private static byte[] deriveOtpKey(byte[] jwtKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtKey, "HmacSHA256"));
            return mac.doFinal(HMAC_DOMAIN);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize reset-code protection");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to validate reset token");
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    public record Accepted(String message) {}
    public record VerifiedReset(String resetToken, int expiresInSeconds) {
        @Override public String toString() { return "VerifiedReset[resetToken=redacted, expiresInSeconds=" + expiresInSeconds + "]"; }
    }
}
