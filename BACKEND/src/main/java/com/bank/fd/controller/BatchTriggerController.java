package com.bank.fd.controller;

import com.bank.fd.service.FdAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/batch")
public class BatchTriggerController {

    @Autowired
    private FdAccountService fdAccountService;

    @PostMapping("/accrue")
    @PreAuthorize("hasAnyRole('BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<String> triggerAccrue() {
        fdAccountService.accrueDailyInterest();
        return ResponseEntity.ok("Daily interest accrual triggered and completed successfully.");
    }

    @PostMapping("/maturity")
    @PreAuthorize("hasAnyRole('BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<String> triggerMaturity() {
        fdAccountService.processMaturedAccounts();
        return ResponseEntity.ok("Maturity processing triggered and completed successfully.");
    }
}
