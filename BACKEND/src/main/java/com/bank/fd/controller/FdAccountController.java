package com.bank.fd.controller;

import com.bank.common.exception.BankingException;
import com.bank.customer.entity.CustomerProfile;
import com.bank.customer.service.CustomerService;
import com.bank.fd.dto.CreateFdRequest;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdTransaction;
import com.bank.fd.repository.FdTransactionRepository;
import com.bank.fd.service.FdAccountService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fd")
public class FdAccountController {

    @Autowired
    private FdAccountService fdAccountService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private FdTransactionRepository fdTransactionRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<FdAccount> bookFd(
            @Valid @RequestBody CreateFdRequest request,
            @RequestParam(required = false) String customerUsername) {

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean isStaff = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_BANK_OFFICER") || a.getAuthority().equals("ROLE_ADMIN"));

        String targetUsername = currentUsername;
        if (isStaff && customerUsername != null && !customerUsername.trim().isEmpty()) {
            targetUsername = customerUsername;
        }

        FdAccount account = fdAccountService.createFdAccount(targetUsername, request);
        return ResponseEntity.ok(account);
    }

    @GetMapping("/my-accounts")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<List<FdAccount>> getMyAccounts() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        List<FdAccount> accounts = fdAccountService.getAccountsByCustomerUsername(username);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{accountNumber}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<FdAccount> getAccountDetails(@PathVariable String accountNumber) {
        FdAccount account = fdAccountService.getAccountByNumber(accountNumber);
        validateOwnershipOrStaff(account);
        return ResponseEntity.ok(account);
    }

    @PostMapping("/{accountNumber}/withdraw")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<FdAccount> withdrawPrematurely(@PathVariable String accountNumber) {
        FdAccount account = fdAccountService.getAccountByNumber(accountNumber);
        validateOwnershipOrStaff(account);
        FdAccount updated = fdAccountService.prematureWithdrawal(accountNumber);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{accountNumber}/transactions")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<List<FdTransaction>> getAccountTransactions(@PathVariable String accountNumber) {
        FdAccount account = fdAccountService.getAccountByNumber(accountNumber);
        validateOwnershipOrStaff(account);
        List<FdTransaction> transactions = fdTransactionRepository.findByAccountNumber(accountNumber);
        return ResponseEntity.ok(transactions);
    }

    private void validateOwnershipOrStaff(FdAccount account) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean isStaff = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_BANK_OFFICER") || a.getAuthority().equals("ROLE_ADMIN"));

        if (!isStaff) {
            CustomerProfile profile = customerService.getProfileEntityByUsername(currentUsername);
            if (!account.getCustomerId().equals(profile.getId())) {
                throw new BankingException("Access Denied: You do not own this Fixed Deposit account.");
            }
        }
    }
}
