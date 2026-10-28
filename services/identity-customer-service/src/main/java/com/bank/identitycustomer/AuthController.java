package com.bank.identitycustomer;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.security.SecureRandom;
import java.util.Base64;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final byte[] jwtKey;
    private final long jwtExpiry;
    private final int customerIdleSeconds, customerMaxSeconds, adminIdleSeconds, adminMaxSeconds;
    private final TotpService totp;
    private final CustomerRiskConfiguration customerRisk;
    private final RestClient mailApi;
    private final String mailFrom;
    private final String mailApiUrl;
    private final String mailApiKey;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, EmailChallenge> emailChallenges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LoginChallenge> loginEmailChallenges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> loginEmailLastSent = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> verifiedEmails = new ConcurrentHashMap<>();

    @Autowired
    public AuthController(JdbcTemplate jdbc, PasswordEncoder passwords,
                          @Value("${app.jwt.secret}") String secret,
                          @Value("${app.jwt.expiration-ms}") long jwtExpiry, TotpService totp, CustomerRiskConfiguration customerRisk,
                          @Value("${app.auth.customer-idle-seconds:300}") int customerIdleSeconds,
                          @Value("${app.auth.customer-max-seconds:1800}") int customerMaxSeconds,
                          @Value("${app.auth.admin-idle-seconds:900}") int adminIdleSeconds,
                          @Value("${app.auth.admin-max-seconds:28800}") int adminMaxSeconds,
                          @Value("${app.auth.password-reset-from:no-reply@localhost}") String mailFrom,
                          @Value("${app.auth.password-reset-api-url:https://api.brevo.com/v3/smtp/email}") String mailApiUrl,
                          RestClient.Builder mailApiBuilder,
                          @Value("${app.auth.password-reset-api-key:}") String mailApiKey) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.jwtKey = Decoders.BASE64.decode(secret);
        this.jwtExpiry = jwtExpiry;
        this.customerIdleSeconds = customerIdleSeconds; this.customerMaxSeconds = customerMaxSeconds;
        this.adminIdleSeconds = adminIdleSeconds; this.adminMaxSeconds = adminMaxSeconds;
        this.totp = totp;
        this.customerRisk = customerRisk;
        this.mailFrom = mailFrom;
        this.mailApiUrl = mailApiUrl;
        this.mailApiKey = mailApiKey;
        this.mailApi = mailApiBuilder.build();
    }

    public AuthController(JdbcTemplate jdbc, PasswordEncoder passwords, String secret, long jwtExpiry,
                          TotpService totp, CustomerRiskConfiguration customerRisk, String mailFrom,
                          String mailApiUrl, RestClient.Builder mailApiBuilder, String mailApiKey) {
        this(jdbc, passwords, secret, jwtExpiry, totp, customerRisk, 300, 1800, 900, 28800,
                mailFrom, mailApiUrl, mailApiBuilder, mailApiKey);
    }

    @PostMapping("/email/verification/send")
    public Message sendEmailVerification(@Valid @RequestBody EmailOtpSendRequest request) {
        String email = request.email.trim().toLowerCase(java.util.Locale.ROOT);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM customer_emails WHERE LOWER(TRIM(cem_email_address))=?", Integer.class, email) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered.");
        if (mailApiKey == null || mailApiKey.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email verification is not configured. Contact the bank team.");
        EmailChallenge prior = emailChallenges.get(email);
        if (prior != null && prior.sentAt().plusSeconds(60).isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait a minute before requesting another code.");
        byte[] bytes = new byte[4]; random.nextBytes(bytes);
        String code = String.format("%06d", Math.floorMod(java.nio.ByteBuffer.wrap(bytes).getInt(), 1_000_000));
        emailChallenges.put(email, new EmailChallenge(code, Instant.now().plusSeconds(600), Instant.now(), 0));
        try {
            Sender sender = parseSender(mailFrom);
            mailApi.post().uri(mailApiUrl)
                    .header("api-key", mailApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("sender", Map.of("name", sender.name(), "email", sender.email()),
                            "to", List.of(Map.of("email", email)), "subject", "Verify your MoneyPal email",
                            "textContent", "Your MoneyPal verification code is " + code + ". It expires in 10 minutes."))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String responseBody = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.warn("Brevo email request failed with HTTP status {}: {}", res.getStatusCode().value(),
                                safeProviderBody(responseBody, code));
                        throw new IllegalStateException("Brevo rejected the email request");
                    })
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            emailChallenges.remove(email);
            if (!(ex instanceof IllegalStateException && "Brevo rejected the email request".equals(ex.getMessage())))
                log.warn("Brevo email request failed before receiving a response (status unavailable).");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The verification email could not be delivered.");
        }
        return new Message("A verification code has been sent.");
    }

    private Sender parseSender(String configured) {
        String value = configured == null ? "" : configured.trim();
        int open = value.lastIndexOf('<');
        int close = value.endsWith(">") ? value.length() - 1 : -1;
        if (open >= 0 && close > open) {
            String name = value.substring(0, open).trim().replaceAll("^\\\"|\\\"$", "").trim();
            return new Sender(name.isBlank() ? "MoneyPal" : name, value.substring(open + 1, close).trim());
        }
        return new Sender("MoneyPal", value);
    }

    private String safeProviderBody(String body, String code) {
        return body.replace(mailApiKey, "[redacted]").replace(code, "[redacted]");
    }

    @PostMapping("/email/verification/verify")
    public Message verifyEmail(@Valid @RequestBody EmailOtpVerifyRequest request) {
        String email = request.email.trim().toLowerCase(java.util.Locale.ROOT);
        EmailChallenge challenge = emailChallenges.get(email);
        if (challenge == null || challenge.expiresAt().isBefore(Instant.now())) {
            emailChallenges.remove(email);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request a new verification code.");
        }
        if (challenge.attempts() >= 5) {
            emailChallenges.remove(email);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many incorrect codes. Request a new code.");
        }
        if (!MessageDigest.isEqual(challenge.code().getBytes(StandardCharsets.US_ASCII), request.code.trim().getBytes(StandardCharsets.US_ASCII))) {
            emailChallenges.put(email, new EmailChallenge(challenge.code(), challenge.expiresAt(), challenge.sentAt(), challenge.attempts() + 1));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The verification code is incorrect.");
        }
        emailChallenges.remove(email);
        verifiedEmails.put(email, Instant.now().plusSeconds(600));
        return new Message("Email verified.");
    }

    @PostMapping("/register") @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public JwtResponse register(@Valid @RequestBody RegisterRequest request) {
        String normalizedEmail = request.email.trim().toLowerCase(java.util.Locale.ROOT);
        Instant verificationExpiry = verifiedEmails.get(normalizedEmail);
        if (verificationExpiry == null || verificationExpiry.isBefore(Instant.now())) {
            verifiedEmails.remove(normalizedEmail);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verify your email before creating an account.");
        }
        String username = request.username.trim();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM user_credentials WHERE LOWER(ucr_username)=LOWER(?)", Integer.class, username) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        if (!PasswordPolicy.isStrong(request.password, username)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PasswordPolicy.ERROR_MESSAGE);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM customer_emails WHERE LOWER(TRIM(cem_email_address))=?", Integer.class, normalizedEmail) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered.");
        try {
            // The unique registry turns the pre-check above into a race-safe claim.
            jdbc.update("INSERT INTO customer_email_registry(cer_normalized_email) VALUES(?)", normalizedEmail);
            jdbc.update("INSERT INTO users(usr_user_type,usr_status) VALUES('CUSTOMER','ACTIVE')");
            long userId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            jdbc.update("INSERT INTO user_credentials(ucr_user_id,ucr_username,ucr_password_hash,ucr_password_changed_at) VALUES(?,?,?,UTC_TIMESTAMP(6))", userId, username, passwords.encode(request.password));
            Integer roleCount = jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE rol_role_code='ROLE_CUSTOMER'", Integer.class);
            if (roleCount == null || roleCount == 0) jdbc.update("INSERT INTO roles(rol_role_code,rol_role_name) VALUES('ROLE_CUSTOMER','Customer')");
            long roleId = jdbc.queryForObject("SELECT rol_role_id FROM roles WHERE rol_role_code='ROLE_CUSTOMER'", Long.class);
            jdbc.update("INSERT INTO user_roles(ur_user_id,ur_role_id) VALUES(?,?)", userId, roleId);
            jdbc.update("INSERT INTO customers(cus_user_id,cus_customer_number,cus_legal_entity_type,cus_risk_category) VALUES(?,?,?,?)", userId, "CUS-"+UUID.randomUUID().toString().replace("-", "").substring(0,12).toUpperCase(), request.legalEntityType, customerRisk.defaultCategory());
            long customerId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            jdbc.update("INSERT INTO customer_details(cud_customer_id,cud_full_name) VALUES(?,?)", customerId, request.fullName);
            jdbc.update("INSERT INTO customer_emails(cem_customer_id,cem_email_address,cem_contact_type,cem_is_primary_signup,cem_is_primary,cem_is_verified,cem_verification_method) VALUES(?,?,'PRIMARY',1,1,1,'EMAIL_OTP')", customerId, normalizedEmail);
            if (request.phone != null && !request.phone.isBlank()) jdbc.update("INSERT INTO customer_phone_numbers(cph_customer_id,cph_phone_number,cph_contact_type,cph_is_primary_signup,cph_is_primary) VALUES(?,?,'PRIMARY',1,1)", customerId, request.phone);
            jdbc.update("INSERT INTO customer_addresses(cad_customer_id,cad_address_type,cad_address_line,cad_city,cad_state,cad_postal_code,cad_is_primary,cad_is_verified,cad_created_by) VALUES(?,'PRIMARY',?,?,?,?,1,0,?)", customerId, request.address, request.city, request.state, request.postalCode, username);
            UserSnapshots.writeUserSnapshot(jdbc, userId);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { verifiedEmails.remove(normalizedEmail, verificationExpiry); }
                });
            } else {
                verifiedEmails.remove(normalizedEmail, verificationExpiry);
            }
            return new JwtResponse(true, null, "Bearer", username, normalizedEmail, List.of("ROLE_CUSTOMER"));
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email or username is already registered.");
        }
    }

    @PostMapping("/login")
    public JwtResponse login(@Valid @RequestBody LoginRequest request) {
        List<Map<String,Object>> rows = LoginIdentifierResolver.resolve(jdbc, request.username, false);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        Map<String,Object> row=rows.get(0); Long userId=((Number)row.get("usr_user_id")).longValue();
        String username = (String) row.get("ucr_username");
        if (!"ACTIVE".equals(row.get("usr_status"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not active");
        if (truthy(row.get("is_locked")))
            throw new ResponseStatusException(HttpStatus.LOCKED, "Account is temporarily locked");
        if (!passwords.matches(request.password, (String)row.get("ucr_password_hash"))) {
            jdbc.update("UPDATE user_credentials SET ucr_failed_attempt_count=ucr_failed_attempt_count+1, ucr_locked_until=CASE WHEN ucr_failed_attempt_count+1>=5 THEN DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 15 MINUTE) ELSE NULL END WHERE ucr_user_id=?",userId);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        List<String> roles=jdbc.queryForList("SELECT r.rol_role_code FROM roles r JOIN user_roles ur ON ur.ur_role_id=r.rol_role_id WHERE ur.ur_user_id=?",String.class,userId);
        if (roles.contains("ROLE_CUSTOMER") && !roles.contains("ROLE_ADMIN")) {
            String email=jdbc.query("SELECT e.cem_email_address FROM customer_emails e JOIN customers c ON c.cus_customer_id=e.cem_customer_id WHERE c.cus_user_id=? AND e.cem_is_primary=1 AND e.cem_is_verified=1 LIMIT 1",rs->rs.next()?rs.getString(1):null,userId);
            if (email == null || mailApiKey == null || mailApiKey.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "A verified email address is required for customer sign-in.");
            String challengeKey = Long.toString(userId);
            Instant lastSent = loginEmailLastSent.get(challengeKey);
            if (lastSent != null && lastSent.plusSeconds(60).isAfter(Instant.now())) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait before requesting another sign-in code.");
            if (lastSent != null) loginEmailLastSent.remove(challengeKey, lastSent);
            String challenge = UUID.randomUUID().toString(); String code = String.format("%06d", random.nextInt(1_000_000)); Instant now = Instant.now();
            loginEmailChallenges.put(challenge, new LoginChallenge(username, email, code, now.plusSeconds(300), now, 0));
            try {
                Sender sender = parseSender(mailFrom);
                mailApi.post().uri(mailApiUrl).header("api-key", mailApiKey).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .body(Map.of("sender", Map.of("name", sender.name(), "email", sender.email()), "to", List.of(Map.of("email", email)), "subject", "Your MoneyPal sign-in code", "textContent", "Your sign-in code is " + code + ". It expires in 5 minutes."))
                        .retrieve().onStatus(HttpStatusCode::isError, (req, res) -> { String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8); log.warn("Sign-in email provider returned HTTP {}: {}", res.getStatusCode().value(), safeProviderBody(body, code)); throw new IllegalStateException("Email provider rejected the request"); }).toBodilessEntity();
            } catch (RuntimeException ex) { loginEmailChallenges.remove(challenge); if (!(ex instanceof IllegalStateException && "Email provider rejected the request".equals(ex.getMessage()))) log.warn("Sign-in email delivery failed before receiving a provider response."); throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The sign-in code could not be delivered."); }
            loginEmailLastSent.put(challengeKey, now);
            return new JwtResponse(true, null, "Bearer", username, maskEmail(email), List.of(), true, challenge);
        }
        jdbc.update("UPDATE user_credentials SET ucr_failed_attempt_count=0,ucr_locked_until=NULL,ucr_last_login_at=UTC_TIMESTAMP(6) WHERE ucr_user_id=?",userId);
        String email=jdbc.query("SELECT e.cem_email_address FROM customer_emails e JOIN customers c ON c.cus_customer_id=e.cem_customer_id WHERE c.cus_user_id=? AND e.cem_is_primary=1 LIMIT 1",rs->rs.next()?rs.getString(1):null,userId);
        return response(username,email,roles);
    }

    @PostMapping("/login/mfa/verify")
    public JwtResponse verifyMfaLogin(@Valid @RequestBody MfaLoginRequest request) {
        LoginChallenge challenge = loginEmailChallenges.get(request.challengeToken);
        if (challenge == null || challenge.expiresAt().isBefore(Instant.now())) { loginEmailChallenges.remove(request.challengeToken); throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired sign-in code"); }
        String username = challenge.username(), challengeEmail = challenge.email(), code = challenge.code();
        if (challenge.attempts() >= 5) { loginEmailChallenges.remove(request.challengeToken); throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many incorrect codes. Sign in again to request a new code."); }
        if (!MessageDigest.isEqual(code.getBytes(StandardCharsets.US_ASCII), request.code.trim().getBytes(StandardCharsets.US_ASCII))) {
            loginEmailChallenges.put(request.challengeToken, new LoginChallenge(challenge.username(), challenge.email(), challenge.code(), challenge.expiresAt(), challenge.sentAt(), challenge.attempts() + 1));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired sign-in code");
        }
        if (!loginEmailChallenges.remove(request.challengeToken, challenge)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired sign-in code");
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT u.usr_user_id,u.usr_status,(c.ucr_locked_until IS NOT NULL AND c.ucr_locked_until>UTC_TIMESTAMP(6)) AS is_locked FROM user_credentials c JOIN users u ON u.usr_user_id=c.ucr_user_id WHERE c.ucr_username=?", username);
        if (rows.isEmpty() || !"ACTIVE".equals(rows.get(0).get("usr_status"))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired sign-in code");
        long userId = ((Number) rows.get(0).get("usr_user_id")).longValue();
        if (truthy(rows.get(0).get("is_locked"))) throw new ResponseStatusException(HttpStatus.LOCKED, "Account is temporarily locked");
        String currentEmail=jdbc.query("SELECT e.cem_email_address FROM customer_emails e JOIN customers c ON c.cus_customer_id=e.cem_customer_id WHERE c.cus_user_id=? AND e.cem_is_primary=1 AND e.cem_is_verified=1 LIMIT 1",rs->rs.next()?rs.getString(1):null,userId);
        if (currentEmail == null || !currentEmail.equalsIgnoreCase(challengeEmail)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired sign-in code");
        jdbc.update("UPDATE user_credentials SET ucr_failed_attempt_count=0,ucr_locked_until=NULL,ucr_last_login_at=UTC_TIMESTAMP(6) WHERE ucr_user_id=?", userId);
        List<String> roles = jdbc.queryForList("SELECT r.rol_role_code FROM roles r JOIN user_roles ur ON ur.ur_role_id=r.rol_role_id WHERE ur.ur_user_id=?", String.class, userId);
        String email = jdbc.query("SELECT e.cem_email_address FROM customer_emails e JOIN customers c ON c.cus_customer_id=e.cem_customer_id WHERE c.cus_user_id=? AND e.cem_is_primary=1 LIMIT 1", rs -> rs.next()?rs.getString(1):null, userId);
        return response(username, email, roles);
    }

    @PostMapping("/mfa/enroll")
    @PreAuthorize("isAuthenticated()")
    public MfaEnrollment enrollMfa(Authentication authentication) {
        String username = authentication.getName();
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT ucr_mfa_enabled FROM user_credentials WHERE ucr_username=?", Boolean.class, username)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MFA is already enabled");
        String secret = totp.newSecret();
        jdbc.update("UPDATE user_credentials SET ucr_mfa_secret=? WHERE ucr_username=?", totp.encrypt(secret), username);
        return new MfaEnrollment(secret, totp.provisioningUri(secret, username));
    }

    @PostMapping("/mfa/enable") @Transactional
    @PreAuthorize("isAuthenticated()")
    public void enableMfa(Authentication authentication, @Valid @RequestBody MfaCodeRequest request) {
        String username = authentication.getName();
        String encrypted = jdbc.query("SELECT ucr_mfa_secret FROM user_credentials WHERE ucr_username=?", rs -> rs.next()?rs.getString(1):null, username);
        Long step = encrypted == null ? null : totp.matchingStep(totp.decrypt(encrypted), request.code);
        long userId = jdbc.queryForObject("SELECT ucr_user_id FROM user_credentials WHERE ucr_username=?", Long.class, username);
        if (step == null || !consumeTotpStep(userId, step)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The authenticator code is invalid or already used");
        jdbc.update("UPDATE user_credentials SET ucr_mfa_enabled=1,ucr_token_version=ucr_token_version+1 WHERE ucr_username=?", username);
    }

    @PostMapping("/mfa/disable") @Transactional
    @PreAuthorize("isAuthenticated()")
    public void disableMfa(Authentication authentication, @Valid @RequestBody MfaCodeRequest request) {
        String username = authentication.getName();
        Map<String,Object> row = jdbc.queryForMap("SELECT ucr_mfa_enabled,ucr_mfa_secret FROM user_credentials WHERE ucr_username=?", username);
        long userId = jdbc.queryForObject("SELECT ucr_user_id FROM user_credentials WHERE ucr_username=?", Long.class, username);
        Long step = !truthy(row.get("ucr_mfa_enabled")) ? null : totp.matchingStep(totp.decrypt((String) row.get("ucr_mfa_secret")), request.code);
        if (step == null || !consumeTotpStep(userId, step)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The authenticator code is invalid or already used");
        jdbc.update("UPDATE user_credentials SET ucr_mfa_enabled=0,ucr_mfa_secret=NULL,ucr_token_version=ucr_token_version+1 WHERE ucr_username=?", username);
    }

    @PostMapping("/refresh") @PreAuthorize("isAuthenticated()") public JwtResponse refresh(Authentication authentication) {
        String username=authentication.getName();
        List<String> roles=jdbc.queryForList("SELECT r.rol_role_code FROM roles r JOIN user_roles ur ON ur.ur_role_id=r.rol_role_id JOIN user_credentials c ON c.ucr_user_id=ur.ur_user_id WHERE c.ucr_username=?",String.class,username);
        String email=jdbc.query("SELECT e.cem_email_address FROM customer_emails e JOIN customers c ON c.cus_customer_id=e.cem_customer_id JOIN user_credentials u ON u.ucr_user_id=c.cus_user_id WHERE u.ucr_username=? AND e.cem_is_primary=1 LIMIT 1",rs->rs.next()?rs.getString(1):null,username);
        String sessionId = (String) authentication.getDetails();
        java.sql.Timestamp expires = jdbc.query("SELECT us_expires_at FROM user_sessions WHERE us_session_id=? AND us_revoked_at IS NULL", rs -> rs.next()?rs.getTimestamp(1):null, sessionId);
        if (expires == null || !expires.toInstant().isAfter(Instant.now())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session has expired.");
        Long userId=jdbc.queryForObject("SELECT ucr_user_id FROM user_credentials WHERE ucr_username=?",Long.class,username);
        Integer tokenVersion=jdbc.queryForObject("SELECT ucr_token_version FROM user_credentials WHERE ucr_username=?",Integer.class,username);
        Date now=new Date(); String token=Jwts.builder().setSubject(username).claim("userId",userId).claim("roles",String.join(",",roles)).claim("tokenVersion",tokenVersion).claim("sid",sessionId).setIssuedAt(now).setExpiration(expires).signWith(Keys.hmacShaKeyFor(jwtKey), SignatureAlgorithm.HS512).compact();
        return new JwtResponse(true,token,username,email,roles);
    }
    @PostMapping("/logout") @PreAuthorize("isAuthenticated()") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(Authentication authentication) {
        String sessionId = (String) authentication.getDetails();
        if (sessionId != null) jdbc.update("UPDATE user_sessions SET us_revoked_at=UTC_TIMESTAMP(6) WHERE us_session_id=? AND us_revoked_at IS NULL", sessionId);
        jdbc.update("UPDATE user_credentials SET ucr_token_version=ucr_token_version+1 WHERE ucr_username=?", authentication.getName());
    }
    @GetMapping("/me") @PreAuthorize("isAuthenticated()") public CustomerResponse me(Authentication authentication) {
        return jdbc.query("SELECT c.cus_customer_id,c.cus_customer_number,c.cus_legal_entity_type,c.cus_kyc_status,c.cus_risk_category,d.cud_full_name FROM user_credentials u JOIN customers c ON c.cus_user_id=u.ucr_user_id LEFT JOIN customer_details d ON d.cud_customer_id=c.cus_customer_id WHERE u.ucr_username=?",rs->{if(!rs.next())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Customer not found");return new CustomerResponse(rs.getLong(1),rs.getString(2),authentication.getName(),rs.getString(6),rs.getString(4),rs.getString(5),rs.getString(3));},authentication.getName());
    }
    @GetMapping("/session") @PreAuthorize("isAuthenticated()")
    public SessionResponse currentSession(Authentication authentication) {
        Map<String,Object> row = jdbc.query("SELECT u.ucr_user_id,c.cus_kyc_status,c.cus_risk_category " +
                        "FROM user_credentials u LEFT JOIN customers c ON c.cus_user_id=u.ucr_user_id WHERE u.ucr_username=?",
                rs -> rs.next() ? Map.of("userId", rs.getLong(1), "kycStatus", rs.getString(2) == null ? "" : rs.getString(2),
                        "riskCategory", rs.getString(3) == null ? "" : rs.getString(3)) : null,
                authentication.getName());
        if (row == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current session is no longer valid.");
        List<String> roles = authentication.getAuthorities().stream().map(a -> a.getAuthority()).sorted().toList();
        return new SessionResponse(((Number) row.get("userId")).longValue(), authentication.getName(), roles,
                (String) row.get("kycStatus"), (String) row.get("riskCategory"));
    }
    @PostMapping("/session/heartbeat") @PreAuthorize("isAuthenticated()") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sessionHeartbeat() { }
    @PostMapping("/change-password") @PreAuthorize("isAuthenticated()") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void changePassword(Authentication authentication,@Valid @RequestBody ChangePasswordRequest request) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT ucr_user_id,ucr_password_hash FROM user_credentials WHERE ucr_username=?",authentication.getName());
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found");
        Map<String,Object> row=rows.get(0);
        if(!passwords.matches(request.currentPassword,(String)row.get("ucr_password_hash"))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Current password is incorrect");
        if(!PasswordPolicy.isStrong(request.newPassword, authentication.getName())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PasswordPolicy.ERROR_MESSAGE);
        if(passwords.matches(request.newPassword,(String)row.get("ucr_password_hash")) || jdbc.queryForList("SELECT uph_password_hash FROM user_password_history WHERE uph_user_id=? ORDER BY uph_changed_at DESC LIMIT 5",String.class,row.get("ucr_user_id")).stream().anyMatch(old->passwords.matches(request.newPassword,old))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a password you have not used before");
        jdbc.update("INSERT INTO user_password_history(uph_user_id,uph_password_hash) VALUES(?,?)",row.get("ucr_user_id"),row.get("ucr_password_hash"));
        jdbc.update("UPDATE user_credentials SET ucr_password_hash=?,ucr_password_changed_at=UTC_TIMESTAMP(6),ucr_must_change_password=0,ucr_token_version=ucr_token_version+1 WHERE ucr_user_id=?",passwords.encode(request.newPassword),row.get("ucr_user_id"));
    }

    private boolean consumeTotpStep(long userId,long step){try{jdbc.update("INSERT INTO mfa_used_totp_steps(mut_user_id,mut_time_step) VALUES(?,?)",userId,step);return true;}catch(DuplicateKeyException ex){return false;}}
    private boolean truthy(Object value){return value instanceof Boolean flag?flag:value instanceof Number number&&number.intValue()!=0;}
    private String maskEmail(String email) { int at=email.indexOf('@'); if(at<2)return "***"+email.substring(Math.max(0,at)); return email.substring(0,2)+"***"+email.substring(at); }

    JwtResponse passwordResetSession(long userId) {
        Map<String, Object> account = jdbc.query("SELECT c.ucr_username,u.usr_status "
                        + "FROM user_credentials c JOIN users u ON u.usr_user_id=c.ucr_user_id "
                        + "WHERE c.ucr_user_id=?",
                rs -> rs.next() ? Map.of("username", rs.getString(1), "status", rs.getString(2)) : null, userId);
        if (account == null || !"ACTIVE".equals(account.get("status")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PasswordRecoveryService.INVALID_TOKEN_MESSAGE);
        List<String> roles = jdbc.queryForList("SELECT r.rol_role_code FROM roles r "
                + "JOIN user_roles ur ON ur.ur_role_id=r.rol_role_id WHERE ur.ur_user_id=?", String.class, userId);
        if (!roles.contains("ROLE_CUSTOMER") || roles.contains("ROLE_ADMIN") || roles.contains("ROLE_BANK_OFFICER"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PasswordRecoveryService.INVALID_TOKEN_MESSAGE);
        String email = jdbc.query("SELECT e.cem_email_address FROM customer_emails e "
                        + "JOIN customers c ON c.cus_customer_id=e.cem_customer_id "
                        + "WHERE c.cus_user_id=? AND e.cem_is_primary=1 AND e.cem_is_verified=1 "
                        + "ORDER BY e.cem_email_id LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, userId);
        if (email == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PasswordRecoveryService.INVALID_TOKEN_MESSAGE);
        return response((String) account.get("username"), email, roles);
    }

    private JwtResponse response(String username,String email,List<String> roles) {
        Long userId=jdbc.query("SELECT ucr_user_id FROM user_credentials WHERE ucr_username=?",rs->rs.next()?rs.getLong(1):null,username);
        Integer tokenVersion=jdbc.query("SELECT ucr_token_version FROM user_credentials WHERE ucr_username=?",rs->rs.next()?rs.getInt(1):0,username);
        boolean customer = roles.contains("ROLE_CUSTOMER") && !roles.contains("ROLE_ADMIN");
        int idleSeconds = customer ? customerIdleSeconds : adminIdleSeconds;
        int maxSeconds = customer ? customerMaxSeconds : adminMaxSeconds;
        Instant issued = Instant.now(); String sessionId = UUID.randomUUID().toString(); Instant expires = issued.plusSeconds(maxSeconds);
        jdbc.update("INSERT INTO user_sessions(us_session_id,us_user_id,us_created_at,us_last_activity_at,us_expires_at,us_idle_timeout_seconds) VALUES(?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?,?)", sessionId, userId, java.sql.Timestamp.from(expires), idleSeconds);
        Date now=Date.from(issued); String token=Jwts.builder().setSubject(username).claim("userId",userId).claim("roles",String.join(",",roles)).claim("tokenVersion",tokenVersion).claim("sid",sessionId).setIssuedAt(now).setExpiration(Date.from(expires)).signWith(Keys.hmacShaKeyFor(jwtKey), SignatureAlgorithm.HS512).compact();
        return new JwtResponse(true,token,username,email,roles);
    }

    public record RegisterRequest(@NotBlank @Size(min=4,max=30) String username,@NotBlank @Size(min=12,max=100) String password,@NotBlank @Email String email,@NotBlank String fullName,String phone,@NotBlank String address,@NotBlank String city,@NotBlank String state,@NotBlank @jakarta.validation.constraints.Pattern(regexp="[0-9]{6}") String postalCode,String legalEntityType) {
        @Override public String toString() { return "RegisterRequest[redacted]"; }
    }
    public record EmailOtpSendRequest(@NotBlank @Email String email) {
        @Override public String toString() { return "EmailOtpSendRequest[redacted]"; }
    }
    public record EmailOtpVerifyRequest(@NotBlank @Email String email,@NotBlank @Size(min=6,max=6) String code) {
        @Override public String toString() { return "EmailOtpVerifyRequest[redacted]"; }
    }
    public record Message(String message) {}
    private record EmailChallenge(String code,Instant expiresAt,Instant sentAt,int attempts) {}
    private record LoginChallenge(String username,String email,String code,Instant expiresAt,Instant sentAt,int attempts) {}
    private record Sender(String name, String email) {}
    public record LoginRequest(@NotBlank @JsonAlias({"identifier","email"}) String username,@NotBlank String password) {
        @Override public String toString() { return "LoginRequest[redacted]"; }
    }
    public record ChangePasswordRequest(@NotBlank String currentPassword,@NotBlank @Size(min=12,max=100) String newPassword) {
        @Override public String toString() { return "ChangePasswordRequest[redacted]"; }
    }
    public record MfaCodeRequest(@NotBlank String code) {
        @Override public String toString() { return "MfaCodeRequest[redacted]"; }
    }
    public record MfaLoginRequest(@NotBlank String challengeToken,@NotBlank String code) {
        @Override public String toString() { return "MfaLoginRequest[redacted]"; }
    }
    public record MfaEnrollment(String secret,String provisioningUri) {
        @Override public String toString() { return "MfaEnrollment[redacted]"; }
    }
    public record CustomerResponse(Long id,String customerNumber,String username,String fullName,String kycStatus,String riskCategory,String legalEntityType) {}
    public record SessionResponse(long userId,String username,List<String> roles,String kycStatus,String riskCategory) {}
    public record JwtResponse(boolean success,String token,String type,String username,String email,List<String> roles,boolean mfaRequired,String mfaToken) {
        public JwtResponse(boolean success,String token,String username,String email,List<String> roles){this(success,token,"Bearer",username,email,roles,false,null);}
        public JwtResponse(boolean success,String token,String type,String username,String email,List<String> roles){this(success,token,type,username,email,roles,false,null);}
        @Override public String toString() { return "JwtResponse[success=" + success + ", token=redacted, type=" + type + ", roles=" + roles + ", mfaRequired=" + mfaRequired + ", mfaToken=redacted]"; }
    }
}
