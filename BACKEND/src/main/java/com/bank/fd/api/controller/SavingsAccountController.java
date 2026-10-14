package com.bank.fd.api.controller;
import com.bank.fd.api.dto.*;
import com.bank.fd.application.SavingsAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/fd/v1/savings-accounts")
@PreAuthorize("isAuthenticated()")
public class SavingsAccountController {
    private final SavingsAccountService savings;
    public SavingsAccountController(SavingsAccountService savings) { this.savings = savings; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public SavingsAccountResponse create(@Valid @RequestBody CreateSavingsRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication auth) {
        return savings.create(auth.getName(), request, key);
    }
    @GetMapping
    public List<SavingsAccountResponse> list(Authentication auth) { return savings.list(auth.getName()); }
    @GetMapping("/{id}")
    public SavingsAccountResponse get(@PathVariable Long id, Authentication auth) { return savings.get(auth.getName(), id); }
}
