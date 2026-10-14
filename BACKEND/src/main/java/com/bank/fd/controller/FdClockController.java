package com.bank.fd.controller;

import com.bank.fd.application.FdBatchProcessingService;
import com.bank.fd.application.FdBatchRunResponse;
import com.bank.fd.domain.FdTimePolicy;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/clock")
public class FdClockController {

    private final FdTimePolicy timePolicy;
    private final FdBatchProcessingService batchService;

    public FdClockController(FdTimePolicy timePolicy, FdBatchProcessingService batchService) {
        this.timePolicy = timePolicy;
        this.batchService = batchService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ClockStatus status() {
        Instant systemTime = timePolicy.systemNow();
        Instant effectiveTime = timePolicy.now();
        return new ClockStatus(systemTime, effectiveTime,
                LocalDate.ofInstant(effectiveTime, timePolicy.businessZone()),
                timePolicy.businessZone().getId(), timePolicy.isSimulated(), null);
    }

    @PostMapping("/advance")
    @PreAuthorize("hasRole('ADMIN')")
    public ClockStatus advance(@Valid @RequestBody AdvanceClockRequest request) {
        timePolicy.advanceDays(request.days());
        FdBatchRunResponse batchRun = batchService.runDaily(null);
        Instant systemTime = timePolicy.systemNow();
        Instant effectiveTime = timePolicy.now();
        return new ClockStatus(systemTime, effectiveTime,
                LocalDate.ofInstant(effectiveTime, timePolicy.businessZone()),
                timePolicy.businessZone().getId(), timePolicy.isSimulated(), batchRun);
    }

    @PostMapping("/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ClockStatus reset() {
        timePolicy.reset();
        return status();
    }

    public record AdvanceClockRequest(
            @NotNull @Positive @Max(FdTimePolicy.MAX_ADVANCE_DAYS) Integer days) { }

    public record ClockStatus(
            @JsonProperty("system_time") Instant systemTime,
            @JsonProperty("effective_time") Instant effectiveTime,
            @JsonProperty("business_date") LocalDate businessDate,
            @JsonProperty("business_zone") String businessZone,
            @JsonProperty("simulation_active") boolean simulationActive,
            @JsonProperty("batch_run") FdBatchRunResponse batchRun) { }
}
