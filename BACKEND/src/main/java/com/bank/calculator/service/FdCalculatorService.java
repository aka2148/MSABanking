package com.bank.calculator.service;

import com.bank.calculator.dto.EstimationRequest;
import com.bank.calculator.dto.EstimationResponse;
import com.bank.customer.entity.CustomerCategory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class FdCalculatorService {

    public static final BigDecimal SENIOR_CITIZEN_BOOST = new BigDecimal("0.50");
    public static final BigDecimal EMPLOYEE_BOOST = new BigDecimal("0.75");
    public static final BigDecimal STANDARD_BOOST = BigDecimal.ZERO;
    public static final BigDecimal PENALTY_RATE = new BigDecimal("1.00"); // 1% penalty

    public BigDecimal getCategoryBoost(CustomerCategory category) {
        if (category == null) {
            return STANDARD_BOOST;
        }
        switch (category) {
            case SENIOR_CITIZEN:
                return SENIOR_CITIZEN_BOOST;
            case EMPLOYEE:
                return EMPLOYEE_BOOST;
            case STANDARD:
            default:
                return STANDARD_BOOST;
        }
    }

    public BigDecimal calculateEffectiveRate(BigDecimal baseRate, CustomerCategory category) {
        BigDecimal boost = getCategoryBoost(category);
        return baseRate.add(boost);
    }

    public EstimationResponse calculateEstimation(EstimationRequest request) {
        BigDecimal effectiveRate = calculateEffectiveRate(request.getBaseInterestRate(), request.getCategory());
        return calculate(request.getPrincipal(), effectiveRate, request.getTermDays(), request.getCompoundingFrequency());
    }

    public EstimationResponse calculate(BigDecimal principal, BigDecimal effectiveRatePercent, int termDays, String compoundingFrequency) {
        double p = principal.doubleValue();
        double r = effectiveRatePercent.doubleValue() / 100.0;
        double t = (double) termDays / 365.0;

        double maturityValue;
        double apyValue;

        if ("SIMPLE".equalsIgnoreCase(compoundingFrequency)) {
            maturityValue = p * (1 + r * t);
            apyValue = r;
        } else {
            int n = getCompoundingPeriodsPerYear(compoundingFrequency);
            maturityValue = p * Math.pow(1 + r / n, n * t);
            apyValue = Math.pow(1 + r / n, n) - 1;
        }

        BigDecimal maturityAmount = BigDecimal.valueOf(maturityValue).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalInterest = maturityAmount.subtract(principal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal apy = BigDecimal.valueOf(apyValue * 100.0).setScale(4, RoundingMode.HALF_UP);

        return EstimationResponse.builder()
                .principal(principal)
                .effectiveInterestRate(effectiveRatePercent)
                .totalInterest(totalInterest)
                .maturityAmount(maturityAmount)
                .apy(apy)
                .build();
    }

    public BigDecimal calculatePrematurePayout(BigDecimal principal, BigDecimal baseRate, int elapsedDays, CustomerCategory category, String compoundingFrequency) {
        if (elapsedDays <= 0) {
            return principal;
        }
        // Penalized rate = baseRate - 1.0% + categoryBoost
        BigDecimal effectivePenalizedRate = baseRate.subtract(PENALTY_RATE).add(getCategoryBoost(category));
        if (effectivePenalizedRate.compareTo(BigDecimal.ZERO) < 0) {
            effectivePenalizedRate = BigDecimal.ZERO;
        }

        EstimationResponse result = calculate(principal, effectivePenalizedRate, elapsedDays, compoundingFrequency);
        return result.getMaturityAmount();
    }

    private int getCompoundingPeriodsPerYear(String frequency) {
        if (frequency == null) {
            return 4; // default to quarterly
        }
        switch (frequency.toUpperCase()) {
            case "MONTHLY":
                return 12;
            case "YEARLY":
                return 1;
            case "QUARTERLY":
            default:
                return 4;
        }
    }
}
