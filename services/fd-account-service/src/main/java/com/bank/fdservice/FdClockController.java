package com.bank.fdservice;

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

@RestController
@RequestMapping("/api/admin/clock")
@PreAuthorize("hasRole('ADMIN')")
public class FdClockController {
    private final DemoClockService clock;
    private final FdBatchService batches;

    public FdClockController(DemoClockService clock, FdBatchService batches) {
        this.clock = clock;
        this.batches = batches;
    }

    @GetMapping
    public ClockStatus status() { return response(clock.status(), null); }

    @PostMapping("/advance")
    public ClockStatus advance(@Valid @RequestBody AdvanceClockRequest request) {
        DemoClockService.ClockState state = clock.advance(request.days());
        return response(state, batches.runDaily(state.businessDate()));
    }

    @PostMapping("/run-daily")
    public ClockStatus runDaily() {
        DemoClockService.ClockState state = clock.status();
        return response(state, batches.runDaily(state.businessDate()));
    }

    @PostMapping("/reset")
    public ClockStatus reset() { return response(clock.reset(), null); }

    private ClockStatus response(DemoClockService.ClockState state, FdBatchService.DailyBatchResult batch) {
        return new ClockStatus(state.systemTime(), state.effectiveTime(), state.businessDate(),
                state.businessZone(), state.simulationActive(), batch);
    }

    public record AdvanceClockRequest(@NotNull @Positive @Max(3650) Integer days) {}
    public record ClockStatus(@JsonProperty("system_time") java.time.Instant systemTime,
                              @JsonProperty("effective_time") java.time.Instant effectiveTime,
                              @JsonProperty("business_date") java.time.LocalDate businessDate,
                              @JsonProperty("business_zone") String businessZone,
                              @JsonProperty("simulation_active") boolean simulationActive,
                              @JsonProperty("batch_run") FdBatchService.DailyBatchResult batchRun) {}
}
