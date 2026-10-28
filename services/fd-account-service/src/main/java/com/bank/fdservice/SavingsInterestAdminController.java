package com.bank.fdservice;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/savings-interest")
@PreAuthorize("hasRole('ADMIN')")
public class SavingsInterestAdminController {
    private final SavingsInterestService interest;private final DemoClockService clock;
    public SavingsInterestAdminController(SavingsInterestService interest,DemoClockService clock){this.interest=interest;this.clock=clock;}
    @PostMapping("/run") public SavingsInterestService.BatchResult run(@Valid @RequestBody(required=false) RunRequest request){return interest.run(request==null||request.businessDate()==null?clock.businessDate():request.businessDate());}
    public record RunRequest(LocalDate businessDate){}
}
