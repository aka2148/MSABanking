package com.bank.productservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/compliance/fd")
@PreAuthorize("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')")
public class FdComplianceController {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private final FdComplianceService compliance;

    public FdComplianceController(FdComplianceService compliance) { this.compliance = compliance; }

    @PostMapping("/verify-rate-disclosure")
    public FdComplianceService.RateDisclosureResponse verifyRate(
            @Valid @RequestBody RateDisclosureRequest request) {
        LocalDate evalDate = request.evaluationDate() != null ? request.evaluationDate() : LocalDate.now(BUSINESS_ZONE);
        return compliance.verifyRate(new FdComplianceService.RateDisclosureRequest(request.productId(),
                request.currencyCode(), request.principalAmount(), request.tenureMonths(), request.displayedRate(),
                evalDate), evalDate);
    }

    @PostMapping("/evaluate-effective-date")
    public FdComplianceService.EffectiveDateResponse evaluateDate(
            @Valid @RequestBody EffectiveDateRequest request) {
        return compliance.evaluateDate(new FdComplianceService.EffectiveDateRequest(request.productId(),
                request.currencyCode(), request.principalAmount(), request.tenureMonths(), request.evaluationDate()));
    }

    @GetMapping("/clock")
    public ComplianceClock clock(@RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate evaluationDate) {
        LocalDate systemDate = LocalDate.now(BUSINESS_ZONE);
        LocalDate effectiveDate = evaluationDate == null ? systemDate : evaluationDate;
        return new ComplianceClock(effectiveDate, systemDate, BUSINESS_ZONE.getId());
    }

    public record RateDisclosureRequest(
            @NotNull @Positive @JsonProperty("product_id") Long productId,
            @NotBlank @JsonProperty("currency_code") String currencyCode,
            @NotNull @Positive @JsonProperty("principal_amount") BigDecimal principalAmount,
            @NotNull @Min(1) @JsonProperty("tenure_months") Integer tenureMonths,
            @NotNull @DecimalMin("0.0") @JsonProperty("displayed_rate") BigDecimal displayedRate,
            @JsonProperty("evaluation_date") LocalDate evaluationDate) {}

    public record EffectiveDateRequest(
            @NotNull @Positive @JsonProperty("product_id") Long productId,
            @NotBlank @JsonProperty("currency_code") String currencyCode,
            @NotNull @Positive @JsonProperty("principal_amount") BigDecimal principalAmount,
            @NotNull @Min(1) @JsonProperty("tenure_months") Integer tenureMonths,
            @NotNull @JsonProperty("evaluation_date") LocalDate evaluationDate) {}

    public record ComplianceClock(@JsonProperty("business_date") LocalDate businessDate,
            @JsonProperty("system_date") LocalDate systemDate,
            @JsonProperty("business_zone") String businessZone) {}
}
