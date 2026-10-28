package com.bank.identitycustomer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Date;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class IdentityCustomerMigrationTest {
    private static final String TEST_JWT_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==";
    private static final AtomicReference<String> TEST_EMAIL_OTP = new AtomicReference<>();
    private static final AtomicReference<String> TEST_PASSWORD_RESET_OTP = new AtomicReference<>();
    private static final AtomicReference<String> RESET_MAIL_BODY = new AtomicReference<>("");
    private static final AtomicReference<String> RESET_MAIL_KEY = new AtomicReference<>("");
    private static final AtomicInteger RESET_MAIL_COUNT = new AtomicInteger();
    private static final AtomicInteger RESET_MAIL_STATUS = new AtomicInteger(201);
    private static final HttpServer MAIL_STUB = createMailStub();

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("moneypal_customer")
            .withUsername("test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20))
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("app.jwt.secret", () -> TEST_JWT_SECRET);
        properties.add("app.security.mfa-encryption-key", () -> TEST_JWT_SECRET);
        properties.add("app.auth.password-reset-api-url", () -> "http://127.0.0.1:" + MAIL_STUB.getAddress().getPort() + "/send");
        properties.add("app.auth.password-reset-api-key", () -> "integration-test-key");
        properties.add("app.auth.password-reset-from", () -> "MoneyPal Tests <noreply@example.test>");
    }

    @AfterAll
    static void stopMailStub() {
        MAIL_STUB.stop(0);
    }

    private static HttpServer createMailStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/send", exchange -> {
                String payload = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                boolean passwordReset = payload.contains("password reset code is");
                var matcher = Pattern.compile("(?:verification|password reset|sign-in) code is (\\d{6})").matcher(payload);
                if (!matcher.find()) {
                    exchange.sendResponseHeaders(400, -1);
                } else {
                    if (passwordReset) {
                        TEST_PASSWORD_RESET_OTP.set(matcher.group(1));
                        RESET_MAIL_BODY.set(payload);
                        RESET_MAIL_KEY.set(exchange.getRequestHeaders().getFirst("api-key"));
                        RESET_MAIL_COUNT.incrementAndGet();
                    } else {
                        TEST_EMAIL_OTP.set(matcher.group(1));
                    }
                    int status = passwordReset ? RESET_MAIL_STATUS.get() : 201;
                    if (status >= 400) {
                        byte[] failure = ("provider echoed " + matcher.group(1) + " integration-test-key").getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(status, failure.length);
                        exchange.getResponseBody().write(failure);
                    } else {
                        exchange.sendResponseHeaders(status, -1);
                    }
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (java.io.IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwords;

    @BeforeEach
    void resetMailState() {
        TEST_EMAIL_OTP.set(null);
        TEST_PASSWORD_RESET_OTP.set(null);
        RESET_MAIL_BODY.set("");
        RESET_MAIL_KEY.set("");
        RESET_MAIL_COUNT.set(0);
        RESET_MAIL_STATUS.set(201);
    }

    @Test
    void startsAgainstItsOwnSchemaAndAppliesCustomerV1() {
        Integer appliedMigrations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1",
                Integer.class);

        assertEquals(1, appliedMigrations);
        assertEquals(16,jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1",Integer.class));
        assertTrue(tableExists("users"));
        assertTrue(tableExists("user_credentials"));
        assertTrue(tableExists("customers"));
        assertTrue(tableExists("outbox_event"));
        assertTrue(tableExists("customer_email_registry"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE rol_role_code='ROLE_BANK_OFFICER'", Integer.class));
        assertTrue(tableExists("password_reset_otps"));
    }

    @Test
    void passwordResetForgotUsesOneResponseAndOnlyMailsEligibleCustomers() throws Exception {
        String unknown = uniqueMobile();
        RecoveryAccount inactive = recoveryAccount("ROLE_CUSTOMER", "INACTIVE", true, true, true);
        RecoveryAccount bankOfficer = recoveryAccount("ROLE_BANK_OFFICER", "ACTIVE", false, false, false);
        RecoveryAccount admin = recoveryAccount("ROLE_ADMIN", "ACTIVE", false, false, false);
        RecoveryAccount noVerifiedEmail = recoveryAccount("ROLE_CUSTOMER", "ACTIVE", true, false, true);
        RecoveryAccount eligible = recoveryAccount("ROLE_CUSTOMER", "ACTIVE", true, true, true);
        jdbc.update("INSERT INTO password_reset_otps(pro_user_id,pro_code_hmac,pro_expires_at,pro_sent_at,pro_created_at) "
                + "VALUES(?,REPEAT('a',64),DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 25 HOUR),DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 25 HOUR),DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 25 HOUR))",
                eligible.userId());

        String expected = PasswordRecoveryService.ACCEPTED_MESSAGE;
        assertEquals(expected, forgotMessage(unknown));
        assertEquals(expected, forgotMessage(inactive.username()));
        assertEquals(expected, forgotMessage(bankOfficer.username()));
        assertEquals(expected, forgotMessage(admin.username()));
        assertEquals(expected, forgotMessage(noVerifiedEmail.username()));
        assertEquals(0, RESET_MAIL_COUNT.get());

        assertEquals(expected, forgotMessage(eligible.mobile()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_otps WHERE pro_user_id=? AND pro_created_at<DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 24 HOUR)", Integer.class, eligible.userId()));
        awaitResetMailCount(1);
        assertEquals("integration-test-key", RESET_MAIL_KEY.get());
        var mail = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(RESET_MAIL_BODY.get());
        assertEquals("MoneyPal Tests", mail.at("/sender/name").asText());
        assertEquals("noreply@example.test", mail.at("/sender/email").asText());
        assertEquals(eligible.email(), mail.at("/to/0/email").asText());
        assertEquals("MoneyPal password reset code", mail.path("subject").asText());
        assertTrue(mail.path("textContent").asText().matches("(?s).*password reset code is [0-9]{6}.*"));
        assertEquals(1, RESET_MAIL_COUNT.get());
    }

    @Test
    void customerCanUsePrimaryPhoneForLoginAndReceivesTheUsernameSession() throws Exception {
        RecoveryAccount account = recoveryAccount("ROLE_CUSTOMER", "ACTIVE", true, true, true);

        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"username\":\"" + account.mobile() + "\",\"password\":\"SkyRiver82!Mint\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(account.username()))
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.mfaToken").isNotEmpty());
    }

    @Test
    void passwordResetStoresOnlyHmacSupersedesCodesAndSignsCustomerInAfterReset() throws Exception {
        RecoveryAccount account = recoveryAccount("ROLE_CUSTOMER", "ACTIVE", true, true, true);
        jdbc.update("UPDATE user_credentials SET ucr_failed_attempt_count=4, "
                + "ucr_locked_until=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 5 MINUTE) WHERE ucr_user_id=?", account.userId());
        jdbc.update("INSERT INTO customer_mpin(cmp_user_id,cmp_hash,cmp_set_at,cmp_failed_attempts,cmp_locked_until,cmp_last_failed_at,cmp_version) "
                + "VALUES(?,'preserve-this-test-hash',UTC_TIMESTAMP(6),3,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE),UTC_TIMESTAMP(6),7)", account.userId());
        List<Map<String, Object>> mpinBefore = jdbc.queryForList("SELECT * FROM customer_mpin WHERE cmp_user_id=?", account.userId());
        List<Map<String, Object>> eventsBefore = jdbc.queryForList("SELECT * FROM customer_mpin_events WHERE user_id=?", account.userId());
        String oldSessionId = UUID.randomUUID().toString();
        String oldToken = tokenForUser(account, 0, oldSessionId);

        assertEquals(PasswordRecoveryService.ACCEPTED_MESSAGE, forgotMessage(account.username()));
        String firstCode = awaitResetOtp();
        Map<String, Object> firstOtp = jdbc.queryForMap("SELECT pro_id,pro_code_hmac,pro_attempts,pro_expires_at,pro_sent_at "
                + "FROM password_reset_otps WHERE pro_user_id=? AND pro_consumed_at IS NULL", account.userId());
        assertEquals(64, firstOtp.get("pro_code_hmac").toString().length());
        assertFalse(firstOtp.values().stream().anyMatch(value -> firstCode.equals(String.valueOf(value))));
        long expirySeconds = java.time.Duration.between(Instant.now(), databaseInstant(firstOtp.get("pro_expires_at"))).getSeconds();
        assertTrue(expirySeconds >= 590 && expirySeconds <= 600);

        jdbc.update("UPDATE password_reset_otps SET pro_sent_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 61 SECOND) "
                + "WHERE pro_user_id=?", account.userId());
        assertEquals(PasswordRecoveryService.ACCEPTED_MESSAGE, forgotMessage(account.username()));
        String currentCode = awaitResetOtp();
        assertNotEquals(firstCode, currentCode);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_otps WHERE pro_user_id=? AND pro_consumed_at IS NULL", Integer.class, account.userId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_otps WHERE pro_user_id=? AND pro_consumed_at IS NOT NULL", Integer.class, account.userId()));

        if (!firstCode.equals(currentCode)) {
            mvc.perform(post("/api/v1/auth/password/verify").contentType("application/json")
                            .content("{\"identifier\":\"" + account.mobile() + "\",\"code\":\"" + firstCode + "\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(PasswordRecoveryService.INVALID_CODE_MESSAGE));
        }
        String verified = mvc.perform(post("/api/v1/auth/password/verify").contentType("application/json")
                        .content("{\"identifier\":\"" + account.mobile() + "\",\"code\":\"" + currentCode + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresInSeconds").value(600))
                .andReturn().getResponse().getContentAsString();
        var verifyBody = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(verified);
        String resetToken = verifyBody.path("resetToken").asText();
        assertFalse(resetToken.isBlank());
        String resetHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resetToken.getBytes(StandardCharsets.UTF_8)));
        assertEquals(resetHash, jdbc.queryForObject("SELECT prt_token_hash FROM password_reset_tokens WHERE prt_user_id=?", String.class, account.userId()));
        assertNotEquals(resetToken, resetHash);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_otps WHERE pro_user_id=? AND pro_consumed_at IS NOT NULL", Integer.class, account.userId()));

        String newPassword = "OceanTrail49#Violet";
        String oldPasswordHash = jdbc.queryForObject("SELECT ucr_password_hash FROM user_credentials WHERE ucr_user_id=?", String.class, account.userId());
        String resetResponse = mvc.perform(post("/api/v1/auth/password/reset").contentType("application/json")
                        .content("{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ROLE_CUSTOMER"))
                .andReturn().getResponse().getContentAsString();
        var session = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(resetResponse);
        String sessionToken = session.path("token").asText();
        assertFalse(sessionToken.isBlank());
        var claims = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_JWT_SECRET)))
                .build().parseClaimsJws(sessionToken).getBody();
        assertEquals(account.userId(), claims.get("userId", Number.class).longValue());
        assertEquals(1, claims.get("tokenVersion", Number.class).intValue());
        assertTrue(passwords.matches(newPassword, jdbc.queryForObject("SELECT ucr_password_hash FROM user_credentials WHERE ucr_user_id=?", String.class, account.userId())));
        assertEquals(0, jdbc.queryForObject("SELECT ucr_failed_attempt_count FROM user_credentials WHERE ucr_user_id=?", Integer.class, account.userId()));
        assertEquals(null, jdbc.queryForObject("SELECT ucr_locked_until FROM user_credentials WHERE ucr_user_id=?", java.sql.Timestamp.class, account.userId()));
        List<String> storedHistory = jdbc.queryForList("SELECT uph_password_hash FROM user_password_history WHERE uph_user_id=?", String.class, account.userId());
        assertEquals(1, storedHistory.size());
        assertEquals(oldPasswordHash, storedHistory.get(0));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE prt_user_id=?", Integer.class, account.userId()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_otps WHERE pro_user_id=?", Integer.class, account.userId()));
        assertEquals(mpinBefore, jdbc.queryForList("SELECT * FROM customer_mpin WHERE cmp_user_id=?", account.userId()));
        assertEquals(eventsBefore, jdbc.queryForList("SELECT * FROM customer_mpin_events WHERE user_id=?", account.userId()));
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + oldToken)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"username\":\"" + account.username() + "\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mfaRequired").value(true));
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"username\":\"" + account.username() + "\",\"password\":\"SkyRiver82!Mint\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/password/reset").contentType("application/json")
                        .content("{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"CloudMap91$Silver\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(PasswordRecoveryService.INVALID_TOKEN_MESSAGE));
    }

    @Test
    void passwordResetAttemptsExpireUniformlyAndAreBurnedOnProviderFailure() throws Exception {
        RecoveryAccount attempts = recoveryAccount("ROLE_CUSTOMER", "ACTIVE", true, true, true);
        assertEquals(PasswordRecoveryService.ACCEPTED_MESSAGE, forgotMessage(attempts.username()));
        String code = awaitResetOtp();
        String wrong = "000000".equals(code) ? "000001" : "000000";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/password/verify").contentType("application/json")
                            .content("{\"identifier\":\"" + attempts.username() + "\",\"code\":\"" + wrong + "\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(PasswordRecoveryService.INVALID_CODE_MESSAGE));
        }
        assertEquals(5, jdbc.queryForObject("SELECT pro_attempts FROM password_reset_otps WHERE pro_user_id=?", Integer.class, attempts.userId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_otps WHERE pro_user_id=? AND pro_consumed_at IS NOT NULL", Integer.class, attempts.userId()));
        mvc.perform(post("/api/v1/auth/password/verify").contentType("application/json")
                        .content("{\"identifier\":\"" + attempts.username() + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(PasswordRecoveryService.INVALID_CODE_MESSAGE));
        mvc.perform(post("/api/v1/auth/password/verify").contentType("application/json")
                        .content("{\"identifier\":\"unknown_" + UUID.randomUUID() + "\",\"code\":\"" + wrong + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(PasswordRecoveryService.INVALID_CODE_MESSAGE));

        RecoveryAccount expired = recoveryAccount("ROLE_CUSTOMER", "ACTIVE", true, true, true);
        assertEquals(PasswordRecoveryService.ACCEPTED_MESSAGE, forgotMessage(expired.username()));
        String expiredCode = awaitResetOtp();
        jdbc.update("UPDATE password_reset_otps SET pro_expires_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE pro_user_id=?", expired.userId());
        mvc.perform(post("/api/v1/auth/password/verify").contentType("application/json")
                        .content("{\"identifier\":\"" + expired.username() + "\",\"code\":\"" + expiredCode + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(PasswordRecoveryService.INVALID_CODE_MESSAGE));

        RecoveryAccount providerFailure = recoveryAccount("ROLE_CUSTOMER", "ACTIVE", true, true, true);
        int mailCountBeforeFailure = RESET_MAIL_COUNT.get();
        RESET_MAIL_STATUS.set(503);
        assertEquals(PasswordRecoveryService.ACCEPTED_MESSAGE, forgotMessage(providerFailure.username()));
        awaitResetMailCount(mailCountBeforeFailure + 1);
        awaitOtpBurn(providerFailure.userId());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_otps WHERE pro_user_id=? AND pro_consumed_at IS NULL", Integer.class, providerFailure.userId()));
        assertEquals(PasswordRecoveryService.ACCEPTED_MESSAGE, forgotMessage(providerFailure.username()));
        assertEquals(mailCountBeforeFailure + 1, RESET_MAIL_COUNT.get());
    }

    @Test
    void passwordResetKeepsTokenOnPolicyFailureThenAllowsOneConcurrentReset() throws Exception {
        RecoveryAccount account = recoveryAccount("ROLE_CUSTOMER", "ACTIVE", true, true, true);
        assertEquals(PasswordRecoveryService.ACCEPTED_MESSAGE, forgotMessage(account.username()));
        String code = awaitResetOtp();
        String verifyResponse = mvc.perform(post("/api/v1/auth/password/verify").contentType("application/json")
                        .content("{\"identifier\":\"" + account.username() + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(verifyResponse).path("resetToken").asText();
        mvc.perform(post("/api/v1/auth/password/reset").contentType("application/json")
                        .content("{\"resetToken\":\"" + token + "\",\"newPassword\":\"Weakpassword12!\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(PasswordPolicy.ERROR_MESSAGE));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE prt_user_id=? AND prt_used_at IS NOT NULL", Integer.class, account.userId()));
        mvc.perform(post("/api/v1/auth/password/reset").contentType("application/json")
                        .content("{\"resetToken\":\"" + token + "\",\"newPassword\":\"SkyRiver82!Mint\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Choose a password you have not used before"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE prt_user_id=? AND prt_used_at IS NOT NULL", Integer.class, account.userId()));
        jdbc.update("INSERT INTO user_password_history(uph_user_id,uph_password_hash) VALUES(?,?)", account.userId(), passwords.encode("PineValley88!Amber"));
        mvc.perform(post("/api/v1/auth/password/reset").contentType("application/json")
                        .content("{\"resetToken\":\"" + token + "\",\"newPassword\":\"PineValley88!Amber\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Choose a password you have not used before"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE prt_user_id=? AND prt_used_at IS NOT NULL", Integer.class, account.userId()));

        String newPassword = "RiverStone37!Cedar";
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> resetStatus(token, newPassword));
            Future<Integer> second = pool.submit(() -> resetStatus(token, newPassword));
            List<Integer> statuses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(value -> value == 200).count());
            assertEquals(1, statuses.stream().filter(value -> value == 400).count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void inAppPasswordChangeRemainsAuthenticatedAndRevokesTheCurrentSession() throws Exception {
        mvc.perform(post("/auth/password/change").contentType("application/json")
                        .content("{\"currentPassword\":\"incorrect\",\"newPassword\":\"RiverStone37!Cedar\"}"))
                .andExpect(status().isForbidden());
        RecoveryAccount account = recoveryAccount("ROLE_CUSTOMER", "ACTIVE", true, true, true);
        assertEquals(PasswordRecoveryService.ACCEPTED_MESSAGE, forgotMessage(account.username()));
        String code = awaitResetOtp();
        String verifyResponse = mvc.perform(post("/api/v1/auth/password/verify").contentType("application/json")
                        .content("{\"identifier\":\"" + account.username() + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String resetToken = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(verifyResponse).path("resetToken").asText();
        String resetResponse = mvc.perform(post("/api/v1/auth/password/reset").contentType("application/json")
                        .content("{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"OceanTrail49#Violet\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String jwt = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(resetResponse).path("token").asText();

        mvc.perform(post("/auth/password/change").header("Authorization", "Bearer " + jwt).contentType("application/json")
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"RiverStone37!Cedar\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Current password is incorrect"));
        mvc.perform(post("/auth/password/change").header("Authorization", "Bearer " + jwt).contentType("application/json")
                        .content("{\"currentPassword\":\"OceanTrail49#Violet\",\"newPassword\":\"Weakpassword12!\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(PasswordPolicy.ERROR_MESSAGE));
        mvc.perform(post("/auth/password/change").header("Authorization", "Bearer " + jwt).contentType("application/json")
                        .content("{\"currentPassword\":\"OceanTrail49#Violet\",\"newPassword\":\"SkyRiver82!Mint\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Choose a password you have not used before"));

        mvc.perform(post("/auth/password/change").header("Authorization", "Bearer " + jwt).contentType("application/json")
                        .content("{\"currentPassword\":\"OceanTrail49#Violet\",\"newPassword\":\"RiverStone37!Cedar\"}"))
                .andExpect(status().isOk());
        assertEquals(2, jdbc.queryForObject("SELECT ucr_token_version FROM user_credentials WHERE ucr_user_id=?", Integer.class, account.userId()));
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + jwt)).andExpect(status().isUnauthorized());
    }

    @Test
    void oldLinkEndpointsAreRemovedWhilePasswordChangeStillRequiresAuthentication() throws Exception {
        mvc.perform(post("/auth/password/forgot").contentType("application/json").content("{\"username\":\"nobody\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/auth/password/reset").contentType("application/json").content("{\"token\":\"old-link\",\"newPassword\":\"OceanTrail49#Violet\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/auth/password/change").contentType("application/json")
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"OceanTrail49#Violet\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationReturnsCustomerTokenAndTokenProtectsMeEndpoint() throws Exception {
        String username = newUsername();
        String token = register(username);
        assertEquals("LOW",jdbc.queryForObject("SELECT c.cus_risk_category FROM customers c JOIN user_credentials u ON u.ucr_user_id=c.cus_user_id WHERE u.ucr_username=?",String.class,username));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.fullName").value("Demo Customer"));
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.kycStatus").value("PENDING"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_CUSTOMER"));
        assertEquals(1,jdbc.queryForObject("SELECT cem_is_verified FROM customer_emails e JOIN customers c ON c.cus_customer_id=e.cem_customer_id JOIN user_credentials u ON u.ucr_user_id=c.cus_user_id WHERE u.ucr_username=?",Integer.class,username));
        Long userId = jdbc.queryForObject("SELECT ucr_user_id FROM user_credentials WHERE ucr_username=?",Long.class,username);
        Integer version = jdbc.queryForObject("SELECT usr_event_version FROM users WHERE usr_user_id=?",Integer.class,userId);
        UserSnapshots.publishInitialUserSnapshot(jdbc,userId);
        assertEquals(version,jdbc.queryForObject("SELECT usr_event_version FROM users WHERE usr_user_id=?",Integer.class,userId));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE event_key=(SELECT usr_user_uuid FROM users WHERE usr_user_id=?) AND topic='user.snapshot'",Integer.class,userId) >= 2);
    }

    @Test
    void logoutRevokesCurrentSessionAndEmailRegistryIsUnique() throws Exception {
        String username = newUsername();
        String token = register(username);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());

        String normalized = "claim-" + UUID.randomUUID() + "@example.test";
        jdbc.update("INSERT INTO customer_email_registry(cer_normalized_email) VALUES(?)", normalized);
        assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> jdbc.update("INSERT INTO customer_email_registry(cer_normalized_email) VALUES(?)", normalized));
    }

    @Test
    void fiveFailedLoginsLockCredentialsForSubsequentAttempts() throws Exception {
        String username = newUsername(); register(username);
        for (int i=0;i<4;i++) mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"username\":\""+username+"\",\"password\":\"incorrect-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"username\":\""+username+"\",\"password\":\"incorrect-password\"}"))
                .andExpect(status().isLocked());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"username\":\""+username+"\",\"password\":\"SkyRiver82!Mint\"}"))
                .andExpect(status().isLocked());
    }

    @Test
    void passwordChangeRequiresCurrentPasswordAndUpdatesLoginCredential() throws Exception {
        String username=newUsername(); String token=register(username);
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"currentPassword\":\"SkyRiver82!Mint\",\"newPassword\":\"OceanTrail49#Violet\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"username\":\""+username+"\",\"password\":\"OceanTrail49#Violet\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ROLE_CUSTOMER"));
    }

    @Test
    void customerAdminApiRequiresOfficerOrAdminRole() throws Exception {
        String username=newUsername(); String customerToken=register(username);
        Long customerId=jdbc.queryForObject("SELECT cus_customer_id FROM customers c JOIN user_credentials u ON u.ucr_user_id=c.cus_user_id WHERE u.ucr_username=?",Long.class,username);
        mvc.perform(get("/api/v1/customers/"+customerId).header("Authorization","Bearer "+customerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/customers/"+customerId).header("Authorization","Bearer "+bankOfficerToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void bankOfficerCanCreateSearchAndUpdateCustomerProfile() throws Exception {
        String username=newUsername();
        KeyHolder key=new GeneratedKeyHolder();
        jdbc.update(connection->{var statement=connection.prepareStatement("INSERT INTO users(usr_user_type,usr_status) VALUES('CUSTOMER','ACTIVE')",Statement.RETURN_GENERATED_KEYS);return statement;},key);
        long userId=key.getKey().longValue();
        jdbc.update("INSERT INTO user_credentials(ucr_user_id,ucr_username,ucr_password_hash) VALUES(?,?,?)",userId,username,"test-hash");
        String admin=bankOfficerToken();
        mvc.perform(post("/api/v1/customers").header("Authorization","Bearer "+admin)
                        .contentType("application/json").content("{\"userId\":"+userId+",\"fullName\":\"Admin Created Customer\",\"kycStatus\":\"VERIFIED\"}"))
                .andExpect(status().isBadRequest());
        String created=mvc.perform(post("/api/v1/customers").header("Authorization","Bearer "+admin)
                        .contentType("application/json").content("{\"userId\":"+userId+",\"fullName\":\"Admin Created Customer\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.riskCategory").value("LOW"))
                .andReturn().getResponse().getContentAsString();
        long customerId=com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(created).get("id").asLong();
        mvc.perform(get("/api/v1/customers/search").header("Authorization","Bearer "+admin).param("q","Admin Created Customer"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(customerId));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/customers/"+customerId)
                        .header("Authorization","Bearer "+admin).contentType("application/json")
                        .content("{\"fullName\":\"Updated Customer\",\"riskCategory\":\"LOW\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Updated Customer"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/customers/"+customerId+"/status")
                        .header("Authorization","Bearer "+admin).contentType("application/json")
                        .content("{\"status\":\"VERIFIED\",\"riskCategory\":\"MEDIUM\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/customers/"+customerId)
                        .header("Authorization","Bearer "+admin).contentType("application/json")
                        .content("{\"kycStatus\":\"VERIFIED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/customers/"+customerId+"/status")
                        .header("Authorization","Bearer "+admin).contentType("application/json").content("{\"riskCategory\":\"MEDIUM\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kycStatus").value("PENDING"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/customers/"+customerId+"/status")
                        .header("Authorization","Bearer "+admin).contentType("application/json").content("{\"riskCategory\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/customers/"+customerId+"/status")
                        .header("Authorization","Bearer "+admin).contentType("application/json").content("{\"riskCategory\":\" \"}"))
                .andExpect(status().isBadRequest());
        var snapshot=com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(jdbc.queryForObject(
                "SELECT payload FROM outbox_event WHERE event_key=(SELECT usr_user_uuid FROM users WHERE usr_user_id=?) ORDER BY source_version DESC LIMIT 1",String.class,userId));
        assertEquals("PENDING",snapshot.path("user").path("kycStatus").asText());
        assertEquals("MEDIUM",snapshot.path("user").path("riskCategory").asText());
        assertEquals(3,snapshot.path("sourceVersion").asInt());
    }

    private String bankOfficerToken() throws Exception {
        String username=newUsername(); register(username);
        long userId=jdbc.queryForObject("SELECT ucr_user_id FROM user_credentials WHERE ucr_username=?",Long.class,username);
        long roleId=jdbc.queryForObject("SELECT rol_role_id FROM roles WHERE rol_role_code='ROLE_BANK_OFFICER'",Long.class);
        jdbc.update("INSERT INTO user_roles(ur_user_id,ur_role_id) VALUES(?,?)",userId,roleId);
        String response=mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"username\":\""+username+"\",\"password\":\"SkyRiver82!Mint\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles").isArray())
                .andReturn().getResponse().getContentAsString();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(response).get("token").asText();
    }

    private String newUsername() { return "demo_" + UUID.randomUUID().toString().substring(0, 8); }

    private String register(String username) throws Exception {
        String email = username + "@example.test";
        mvc.perform(post("/api/v1/auth/email/verification/send").contentType("application/json")
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
        String code = TEST_EMAIL_OTP.getAndSet(null);
        assertTrue(code != null && code.matches("\\d{6}"));
        mvc.perform(post("/api/v1/auth/email/verification/verify").contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());
        String response=mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .content("{\"username\":\""+username+"\",\"password\":\"SkyRiver82!Mint\",\"email\":\""+email+"\",\"fullName\":\"Demo Customer\",\"address\":\"12 Sample Road\",\"city\":\"Sample City\",\"state\":\"Sample State\",\"postalCode\":\"560001\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.roles[0]").value("ROLE_CUSTOMER"))
                .andReturn().getResponse().getContentAsString();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(response).get("token").asText();
    }

    private String forgotMessage(String identifier) throws Exception {
        TEST_PASSWORD_RESET_OTP.set(null);
        String response = mvc.perform(post("/api/v1/auth/password/forgot").contentType("application/json")
                        .content("{\"identifier\":\"" + identifier + "\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(response).path("message").asText();
    }

    private String awaitResetOtp() throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (TEST_PASSWORD_RESET_OTP.get() == null && System.currentTimeMillis() < deadline) Thread.sleep(20);
        assertTrue(TEST_PASSWORD_RESET_OTP.get() != null, "mock mail sink did not receive the reset code");
        return TEST_PASSWORD_RESET_OTP.get();
    }

    private void awaitResetMailCount(int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (RESET_MAIL_COUNT.get() < expected && System.currentTimeMillis() < deadline) Thread.sleep(20);
        assertEquals(expected, RESET_MAIL_COUNT.get());
    }

    private void awaitOtpBurn(long userId) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_otps WHERE pro_user_id=? AND pro_consumed_at IS NULL", Integer.class, userId);
            if (active != null && active == 0) return;
            Thread.sleep(20);
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_otps WHERE pro_user_id=? AND pro_consumed_at IS NULL", Integer.class, userId));
    }

    private Instant databaseInstant(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.LocalDateTime localDateTime) return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        throw new AssertionError("Unexpected database timestamp type: " + (value == null ? "null" : value.getClass().getName()));
    }

    private int resetStatus(String token, String newPassword) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/reset").contentType("application/json")
                        .content("{\"resetToken\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private RecoveryAccount recoveryAccount(String roleCode, String status, boolean customer, boolean verifiedEmail, boolean withPhone) {
        String username = "recovery_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String email = username + "@example.test";
        String mobile = uniqueMobile();
        KeyHolder userKey = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO users(usr_user_type,usr_status) VALUES(?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, customer ? "CUSTOMER" : "STAFF");
            statement.setString(2, status);
            return statement;
        }, userKey);
        long userId = userKey.getKey().longValue();
        jdbc.update("INSERT INTO user_credentials(ucr_user_id,ucr_username,ucr_password_hash,ucr_password_changed_at) "
                + "VALUES(?,?,?,UTC_TIMESTAMP(6))", userId, username, passwords.encode("SkyRiver82!Mint"));
        jdbc.update("INSERT IGNORE INTO roles(rol_role_code,rol_role_name) VALUES(?,?)", roleCode, roleCode.substring("ROLE_".length()));
        long roleId = jdbc.queryForObject("SELECT rol_role_id FROM roles WHERE rol_role_code=?", Long.class, roleCode);
        jdbc.update("INSERT INTO user_roles(ur_user_id,ur_role_id) VALUES(?,?)", userId, roleId);
        long customerId = -1L;
        if (customer) {
            jdbc.update("INSERT INTO customers(cus_user_id,cus_customer_number,cus_legal_entity_type,cus_kyc_status,cus_risk_category) "
                    + "VALUES(?,?,'INDIVIDUAL','PENDING','LOW')", userId, "R" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            customerId = jdbc.queryForObject("SELECT cus_customer_id FROM customers WHERE cus_user_id=?", Long.class, userId);
            jdbc.update("INSERT INTO customer_emails(cem_customer_id,cem_contact_type,cem_email_address,cem_is_primary_signup,cem_is_primary,cem_is_verified) "
                    + "VALUES(?,'PERSONAL',?,1,1,?)", customerId, email, verifiedEmail ? 1 : 0);
            if (withPhone) jdbc.update("INSERT INTO customer_phone_numbers(cph_customer_id,cph_contact_type,cph_phone_number,cph_is_primary_signup,cph_is_primary) "
                    + "VALUES(?,'PRIMARY',?,1,1)", customerId, mobile);
        }
        return new RecoveryAccount(userId, customerId, username, mobile, email);
    }

    private String uniqueMobile() {
        return String.format(java.util.Locale.ROOT, "%010d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 10_000_000_000L));
    }

    private String tokenForUser(RecoveryAccount account, int tokenVersion, String sessionId) {
        var expiry = Instant.now().plusSeconds(1800);
        jdbc.update("INSERT INTO user_sessions(us_session_id,us_user_id,us_created_at,us_last_activity_at,us_expires_at,us_idle_timeout_seconds) "
                + "VALUES(?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?,300)", sessionId, account.userId(), java.sql.Timestamp.from(expiry));
        return Jwts.builder().setSubject(account.username()).claim("userId", account.userId())
                .claim("roles", "ROLE_CUSTOMER").claim("tokenVersion", tokenVersion).claim("sid", sessionId)
                .setIssuedAt(new Date()).setExpiration(Date.from(expiry))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_JWT_SECRET)), SignatureAlgorithm.HS512).compact();
    }

    private record RecoveryAccount(long userId, long customerId, String username, String mobile, String email) {}

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                table);
        return count != null && count == 1;
    }
}
