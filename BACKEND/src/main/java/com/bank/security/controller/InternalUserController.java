package com.bank.security.controller;

import com.bank.customer.dto.InternalCredentialValidationRequest;
import com.bank.customer.dto.InternalLoginAttemptRequest;
import com.bank.customer.dto.InternalPasswordChangeRequest;
import com.bank.customer.service.InternalUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {
    private final InternalUserService service;

    public InternalUserController(InternalUserService service) { this.service = service; }

    @PostMapping("/validate-credentials")
    public Map<String, Object> validateCredentials(@Valid @RequestBody InternalCredentialValidationRequest request) {
        return service.validateCredentials(request);
    }

    @PostMapping("/{userId}/record-login-attempt")
    public Map<String, Object> recordLoginAttempt(@PathVariable Long userId,
                                                   @RequestBody InternalLoginAttemptRequest request) {
        return service.recordLoginAttempt(userId, request);
    }

    @GetMapping("/{userId}/account-status")
    public Map<String, Object> accountStatus(@PathVariable Long userId) { return service.accountStatus(userId); }

    @GetMapping("/{userId}/roles")
    public List<String> roles(@PathVariable Long userId) { return service.roles(userId); }

    @GetMapping("/{userId}/contacts")
    public Map<String, Object> contacts(@PathVariable Long userId) { return service.contacts(userId); }

    @PostMapping("/{userId}/password")
    public ResponseEntity<Void> changePassword(@PathVariable Long userId,
                                               @Valid @RequestBody InternalPasswordChangeRequest request) {
        service.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }
}