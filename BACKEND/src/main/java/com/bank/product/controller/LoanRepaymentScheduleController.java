package com.bank.product.controller;

import com.bank.product.dto.request.CreateLoanRepaymentScheduleRequest;
import com.bank.product.dto.request.UpdateLoanRepaymentScheduleRequest;
import com.bank.product.dto.response.LoanRepaymentScheduleResponse;
import com.bank.product.service.LoanRepaymentScheduleService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/loan-repayment-schedules")
public class LoanRepaymentScheduleController {

    private final LoanRepaymentScheduleService service;

    public LoanRepaymentScheduleController(
            LoanRepaymentScheduleService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<LoanRepaymentScheduleResponse> create(
            @Valid @RequestBody
            CreateLoanRepaymentScheduleRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createLoanRepaymentSchedule(request));
    }

    @GetMapping
    public ResponseEntity<List<LoanRepaymentScheduleResponse>>
    getAll() {

        return ResponseEntity.ok(
                service.getAllLoanRepaymentSchedules());
    }

    @GetMapping("/{scheduleId}")
    public ResponseEntity<LoanRepaymentScheduleResponse> getById(
            @PathVariable Integer scheduleId) {

        return ResponseEntity.ok(
                service.getLoanRepaymentScheduleById(scheduleId));
    }

    @GetMapping("/loan-account/{loanAccountNo}")
    public ResponseEntity<List<LoanRepaymentScheduleResponse>>
    getByLoanAccountNo(@PathVariable String loanAccountNo) {

        return ResponseEntity.ok(
                service.getLoanRepaymentSchedulesByLoanAccountNo(
                        loanAccountNo));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<LoanRepaymentScheduleResponse>>
    getByProductId(@PathVariable Integer productId) {

        return ResponseEntity.ok(
                service.getLoanRepaymentSchedulesByProductId(productId));
    }

    @PutMapping("/{scheduleId}")
    public ResponseEntity<LoanRepaymentScheduleResponse> update(
            @PathVariable Integer scheduleId,
            @Valid @RequestBody
            UpdateLoanRepaymentScheduleRequest request) {

        return ResponseEntity.ok(
                service.updateLoanRepaymentSchedule(
                        scheduleId, request));
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> delete(
            @PathVariable Integer scheduleId) {

        service.deleteLoanRepaymentSchedule(scheduleId);

        return ResponseEntity.noContent().build();
    }
}