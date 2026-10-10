package com.bank.fd.controller;

import com.bank.fd.application.FdBatchProcessingService;
import com.bank.fd.application.FdBatchRunResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/admin/batch")
public class BatchTriggerController {

    private final FdBatchProcessingService batchService;

    public BatchTriggerController(FdBatchProcessingService batchService) {
        this.batchService = batchService;
    }

    @PostMapping("/accrue")
    @PreAuthorize("hasAnyRole('BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<FdBatchRunResponse> triggerAccrue() {
        return ResponseEntity.ok(batchService.runAccrual(null));
    }

    @PostMapping("/maturity")
    @PreAuthorize("hasAnyRole('BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<FdBatchRunResponse> triggerMaturity() {
        return ResponseEntity.ok(batchService.runMaturity(null));
    }

    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<FdBatchRunResponse> triggerDailyBatch() {
        return ResponseEntity.ok(batchService.runDaily(null));
    }
}
