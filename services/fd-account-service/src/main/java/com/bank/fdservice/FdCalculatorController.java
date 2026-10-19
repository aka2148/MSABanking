package com.bank.fdservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;

@RestController
@RequestMapping("/api/calculator")
public class FdCalculatorController {
    private static final BigDecimal SENIOR_BOOST = new BigDecimal("0.50");
    private static final BigDecimal EMPLOYEE_BOOST = new BigDecimal("0.75");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @PostMapping("/estimate")
    public EstimationResponse estimate(@Valid @RequestBody EstimationRequest request) {
        BigDecimal boost = switch (request.category()) {
            case SENIOR_CITIZEN -> SENIOR_BOOST;
            case EMPLOYEE -> EMPLOYEE_BOOST;
            default -> BigDecimal.ZERO;
        };
        BigDecimal effectiveRate = request.baseInterestRate().add(boost);
        int periods = periods(request.compoundingFrequency());
        BigDecimal annualRate = effectiveRate.divide(HUNDRED, 16, RoundingMode.HALF_UP);
        BigDecimal maturityFactor;
        BigDecimal apyPercent;
        if ("SIMPLE".equalsIgnoreCase(request.compoundingFrequency())) {
            maturityFactor = BigDecimal.ONE.add(annualRate.multiply(BigDecimal.valueOf(request.termDays()))
                    .divide(BigDecimal.valueOf(365), 16, RoundingMode.HALF_UP));
            apyPercent = effectiveRate;
        } else {
            // Java has no fractional BigDecimal power; retain the legacy compound formula for the factor,
            // while keeping principal and all monetary rounding in BigDecimal.
            double periodicRate = annualRate.doubleValue() / periods;
            double elapsedPeriods = (double) periods * request.termDays() / 365.0;
            double factor = Math.pow(1.0 + periodicRate, elapsedPeriods);
            maturityFactor = BigDecimal.valueOf(factor);
            double apy = (Math.pow(1.0 + periodicRate, periods) - 1.0) * 100.0;
            apyPercent = BigDecimal.valueOf(apy);
        }
        BigDecimal maturity = request.principal().multiply(maturityFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal interest = maturity.subtract(request.principal()).setScale(2, RoundingMode.HALF_UP);
        return new EstimationResponse(request.principal(), effectiveRate, interest, maturity, apyPercent.setScale(4, RoundingMode.HALF_UP));
    }

    private int periods(String frequency) {
        if (frequency == null) return 4;
        return switch (frequency.toUpperCase()) {
            case "MONTHLY" -> 12;
            case "YEARLY" -> 1;
            case "QUARTERLY" -> 4;
            default -> 4;
        };
    }

    public enum CustomerCategory { STANDARD, SENIOR_CITIZEN, EMPLOYEE }
    public record EstimationRequest(@NotNull @DecimalMin("1.00") BigDecimal principal,
                                    @NotNull @DecimalMin("0.01") BigDecimal baseInterestRate,
                                    @NotNull @Min(1) Integer termDays,
                                    @NotNull CustomerCategory category,
                                    @NotBlank String compoundingFrequency) {}
    public record EstimationResponse(BigDecimal principal, BigDecimal effectiveInterestRate, BigDecimal totalInterest,
                                     BigDecimal maturityAmount, BigDecimal apy) {}
}
