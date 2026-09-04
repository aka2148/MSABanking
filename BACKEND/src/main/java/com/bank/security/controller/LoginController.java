package com.bank.security.controller;

import com.bank.common.exception.InvalidInputException;
import com.bank.security.service.OtpService;
import com.bank.security.service.SessionService;
import com.bank.security.userservice.UserServiceClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class LoginController {
    private final UserServiceClient userService;
    private final SessionService sessions;
    private final OtpService otpService;

    public LoginController(UserServiceClient userService, SessionService sessions, OtpService otpService) {
        this.userService = userService;
        this.sessions = sessions;
        this.otpService = otpService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        UserServiceClient.CredentialValidation validation = userService.validateCredentials(request.username(), request.password());
        if (validation == null || !validation.valid()) {
            throw new AuthenticationFailureException();
        }
        UserServiceClient.AccountStatus status = userService.getAccountStatus(validation.userId());
        if (status.locked() || status.suspended()) {
            throw new AuthenticationFailureException();
        }
        if (validation.mfaEnabled()) {
            OtpService.Challenge challenge = createOtp(validation, "MFA");
            return ResponseEntity.ok(Map.of("status", "MFA_REQUIRED", "challengeId", challenge.challengeId()));
        }
        return issueSession(validation, httpRequest);
    }

    @PostMapping("/login/mfa/challenge")
    public Map<String, String> mfaChallenge(@Valid @RequestBody ChallengeRequest request) {
        OtpService.Challenge challenge = otpService.send(request.userId(), request.username(), request.destination(), "MFA");
        return Map.of("challengeId", challenge.challengeId(), "status", "OTP_SENT");
    }

    @PostMapping("/login/mfa/verify")
    public ResponseEntity<?> mfaVerify(@Valid @RequestBody OtpVerifyRequest request, HttpServletRequest httpRequest) {
        OtpService.Challenge challenge = otpService.verify(request.challengeId(), request.otp());
        if (challenge == null) throw new AuthenticationFailureException();
        UserServiceClient.AccountStatus status = userService.getAccountStatus(challenge.userId());
        if (status.locked() || status.suspended()) throw new AuthenticationFailureException();
        return issueSession(new UserServiceClient.CredentialValidation(true, challenge.userId(), challenge.username(), true), httpRequest);
    }

    @PostMapping("/token/refresh")
    public Map<String, String> refresh(@Valid @RequestBody RefreshRequest request) {
        SessionService.Session session = sessions.refresh(request.refreshToken());
        if (session == null) throw new AuthenticationFailureException();
        return sessionResponse(session);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        String token = bearerToken(request);
        if (token != null) {
            SessionService.Session session = sessions.fromAccessToken(token);
            if (session != null) sessions.revoke(session.sessionId());
        }
    }

    @GetMapping("/session")
    public Map<String, Object> session(HttpServletRequest request) {
        SessionService.Session session = requiredSession(request);
        return Map.of("active", true, "userId", session.userId(), "username", session.username(), "roles", session.roles());
    }

    @GetMapping("/account-status")
    public UserServiceClient.AccountStatus accountStatus(@RequestParam String userId) {
        return userService.getAccountStatus(userId);
    }

    @PostMapping("/password/change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody PasswordChangeRequest request, HttpServletRequest httpRequest) {
        SessionService.Session session = requiredSession(httpRequest);
        userService.changePassword(session.userId(), request.currentPassword(), request.newPassword());
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody UsernameRequest request) {
        userService.requestPasswordReset(request.username());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        userService.resetPassword(request.token(), request.newPassword());
    }

    @PostMapping("/otp/send")
    public Map<String, String> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        UserServiceClient.Contacts contacts = userService.getContacts(request.userId());
        String destination = "PHONE".equalsIgnoreCase(request.channel()) ? contacts.phone() : contacts.email();
        if (destination == null || destination.isBlank()) throw new InvalidInputException("No verified contact available");
        OtpService.Challenge challenge = otpService.send(request.userId(), request.username(), destination, "OTP_LOGIN");
        return Map.of("challengeId", challenge.challengeId(), "status", "OTP_SENT");
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody OtpVerifyRequest request, HttpServletRequest httpRequest) {
        OtpService.Challenge challenge = otpService.verify(request.challengeId(), request.otp());
        if (challenge == null) throw new AuthenticationFailureException();
        return issueSession(new UserServiceClient.CredentialValidation(true, challenge.userId(), challenge.username(), false), httpRequest);
    }

    private OtpService.Challenge createOtp(UserServiceClient.CredentialValidation validation, String purpose) {
        UserServiceClient.Contacts contacts = userService.getContacts(validation.userId());
        String destination = contacts.email() != null ? contacts.email() : contacts.phone();
        if (destination == null) throw new InvalidInputException("No verified contact available");
        return otpService.send(validation.userId(), validation.username(), destination, purpose);
    }

    private ResponseEntity<Map<String, String>> issueSession(UserServiceClient.CredentialValidation validation,
                                                              HttpServletRequest request) {
        List<String> roles = userService.getRoles(validation.userId());
        userService.reportLoginAttempt(validation.userId(), true, request.getRemoteAddr());
        SessionService.Session session = sessions.issue(validation.userId(), validation.username(), roles);
        return ResponseEntity.ok(sessionResponse(session));
    }

    private Map<String, String> sessionResponse(SessionService.Session session) {
        return Map.of("accessToken", sessions.accessToken(session), "refreshToken", session.refreshToken(), "tokenType", "Bearer");
    }

    private SessionService.Session requiredSession(HttpServletRequest request) {
        SessionService.Session session = sessions.fromAccessToken(bearerToken(request));
        if (session == null) throw new AuthenticationFailureException();
        return session;
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record ChallengeRequest(@NotBlank String userId, @NotBlank String username, @NotBlank String destination) {}
    public record OtpVerifyRequest(@NotBlank String challengeId, @NotBlank String otp) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}
    public record PasswordResetRequest(@NotBlank String token, @NotBlank String newPassword) {}
    public record UsernameRequest(@NotBlank String username) {}
    public record OtpSendRequest(@NotBlank String userId, @NotBlank String username, String channel) {}

    public static class AuthenticationFailureException extends RuntimeException {
        public AuthenticationFailureException() { super("Authentication failed"); }
    }
}