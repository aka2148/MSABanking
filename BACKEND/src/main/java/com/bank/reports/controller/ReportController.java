package com.bank.reports.controller;

import com.bank.reports.dto.BankOperationalReport;
import com.bank.reports.dto.CustomerPortfolioReport;
import com.bank.reports.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/portfolio")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<CustomerPortfolioReport> getMyPortfolio() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        CustomerPortfolioReport report = reportService.getCustomerPortfolioReport(username);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/portfolio/{username}")
    @PreAuthorize("hasAnyRole('BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<CustomerPortfolioReport> getCustomerPortfolio(@PathVariable String username) {
        CustomerPortfolioReport report = reportService.getCustomerPortfolioReport(username);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/operational")
    @PreAuthorize("hasAnyRole('BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<BankOperationalReport> getBankOperationalMetrics() {
        BankOperationalReport report = reportService.getBankOperationalReport();
        return ResponseEntity.ok(report);
    }
}
