package com.bank.customer.service;

import com.bank.common.exception.InvalidInputException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.customer.dto.InternalCredentialValidationRequest;
import com.bank.customer.dto.InternalLoginAttemptRequest;
import com.bank.customer.dto.InternalPasswordChangeRequest;
import com.bank.customer.entity.CustomerEmail;
import com.bank.customer.entity.CustomerPhone;
import com.bank.customer.entity.CustomerProfile;
import com.bank.customer.entity.User;
import com.bank.customer.entity.UserCredential;
import com.bank.customer.entity.UserPasswordHistory;
import com.bank.customer.entity.UserRole;
import com.bank.customer.repository.CustomerEmailRepository;
import com.bank.customer.repository.CustomerPhoneRepository;
import com.bank.customer.repository.CustomerProfileRepository;
import com.bank.customer.repository.UserPasswordHistoryRepository;
import com.bank.customer.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InternalUserService {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    private final UserRepository users;
    private final UserPasswordHistoryRepository passwordHistory;
    private final CustomerProfileRepository customers;
    private final CustomerPhoneRepository phones;
    private final CustomerEmailRepository emails;
    private final PasswordEncoder passwordEncoder;

    public InternalUserService(UserRepository users, UserPasswordHistoryRepository passwordHistory,
                               CustomerProfileRepository customers, CustomerPhoneRepository phones,
                               CustomerEmailRepository emails, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordHistory = passwordHistory;
        this.customers = customers;
        this.phones = phones;
        this.emails = emails;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> validateCredentials(InternalCredentialValidationRequest request) {
        User user = users.findByCredentialsUsername(request.username()).orElse(null);
        if (user == null || user.getCredentials() == null || isLocked(user)
                || !passwordEncoder.matches(request.password(), user.getCredentials().getPasswordHash())) {
            return Map.of("valid", false);
        }
        return Map.of("valid", true, "userId", user.getId(), "status", user.getStatus(),
                "mustChangePassword", user.getCredentials().isMustChangePassword(),
                "mfaEnabled", user.getCredentials().isMfaEnabled());
    }

    @Transactional
    public Map<String, Object> recordLoginAttempt(Long userId, InternalLoginAttemptRequest request) {
        User user = user(userId);
        UserCredential credentials = user.getCredentials();
        if (credentials == null) throw new ResourceNotFoundException("Credentials not found");

        if (request.successful()) {
            credentials.setFailedAttemptCount(0);
            credentials.setLockedUntil(null);
        } else {
            int failures = credentials.getFailedAttemptCount() + 1;
            credentials.setFailedAttemptCount(failures);
            if (failures >= MAX_FAILED_ATTEMPTS) {
                credentials.setLockedUntil(OffsetDateTime.now().plusMinutes(LOCK_MINUTES));
            }
        }
        users.save(user);
        return accountStatus(user);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> accountStatus(Long userId) { return accountStatus(user(userId)); }

    @Transactional(readOnly = true)
    public List<String> roles(Long userId) {
        return user(userId).getRoleAssignments().stream().map(UserRole::getRoleCode).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> contacts(Long userId) {
        CustomerProfile customer = customers.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
        List<Map<String, Object>> verifiedPhones = phones.findByCustomerId(customer.getId()).stream()
                .filter(CustomerPhone::isVerified)
                .map(phone -> Map.<String, Object>of("countryCode", phone.getCountryCode(), "phoneNumber", phone.getPhoneNumber(),
                        "contactType", phone.getContactType(), "primary", phone.isPrimaryPhone()))
                .toList();
        List<Map<String, Object>> verifiedEmails = emails.findByCustomerId(customer.getId()).stream()
                .filter(CustomerEmail::isVerified)
                .map(email -> Map.<String, Object>of("emailAddress", email.getEmailAddress(),
                        "contactType", email.getContactType(), "primary", email.isPrimaryEmail()))
                .toList();
        return Map.of("phones", verifiedPhones, "emails", verifiedEmails);
    }

    @Transactional
    public void changePassword(Long userId, InternalPasswordChangeRequest request) {
        User user = user(userId);
        UserCredential credentials = user.getCredentials();
        if (credentials == null) throw new ResourceNotFoundException("Credentials not found");
        if (!request.reset() && (request.currentPassword() == null
                || !passwordEncoder.matches(request.currentPassword(), credentials.getPasswordHash()))) {
            throw new InvalidInputException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), credentials.getPasswordHash())
                || passwordHistory.findByUserIdOrderByChangedAtDesc(userId).stream()
                .anyMatch(previous -> passwordEncoder.matches(request.newPassword(), previous.getPasswordHash()))) {
            throw new InvalidInputException("Password was used recently");
        }
        UserPasswordHistory previous = new UserPasswordHistory();
        previous.setUser(user);
        previous.setPasswordHash(credentials.getPasswordHash());
        passwordHistory.save(previous);
        credentials.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        credentials.setMustChangePassword(false);
        users.save(user);
    }

    private User user(Long userId) {
        return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private boolean isLocked(User user) {
        return user.getCredentials().getLockedUntil() != null
                && user.getCredentials().getLockedUntil().isAfter(OffsetDateTime.now());
    }

    private Map<String, Object> accountStatus(User user) {
        UserCredential credentials = user.getCredentials();
        Map<String, Object> status = new HashMap<>();
        status.put("userId", user.getId());
        status.put("status", user.getStatus());
        status.put("lockedUntil", credentials == null ? null : credentials.getLockedUntil());
        status.put("mustChangePassword", credentials != null && credentials.isMustChangePassword());
        return status;
    }
}