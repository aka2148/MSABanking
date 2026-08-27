package com.bank.calculator.service;

import com.bank.calculator.dto.EstimationRequest;
import com.bank.calculator.dto.EstimationResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FdCalculatorService {

    // Maximum combined add-on cap
    private static final BigDecimal MAX_SEGMENT_ADDON_CAP = new BigDecimal("2.00");

    // Dynamic Category benefits
    private static final Map<String, BigDecimal> SEGMENT_RATES = Map.of(
            "GENERAL", BigDecimal.ZERO,
            "SENIOR", new BigDecimal("1.00"),
            "SENIOR_CITIZEN", new BigDecimal("1.00"),
            "EMPLOYEE", new BigDecimal("1.00"),
            "STUDENT", new BigDecimal("0.50"),
            "PREMIUM_USER", new BigDecimal("1.50")
    );

    public EstimationResponse calculateMaturityAmount(EstimationRequest request) {
        // 1. Resolve Base Rate
        BigDecimal baseRate = (request.getBaseRate() != null && request.getBaseRate().compareTo(BigDecimal.ZERO) > 0)
                ? request.getBaseRate()
                : resolveBaseRate(request.getTenure());

        // 2. Category Logic: evaluate additional rates
        Map<String, BigDecimal> appliedSegments = new LinkedHashMap<>();
        BigDecimal rawAddOn = BigDecimal.ZERO;
        List<String> segments = request.getCustomerSegments();

        if (segments != null && !segments.isEmpty()) {
            for (String segment : segments) {
                if (segment != null) {
                    String key = segment.trim().toUpperCase().replace(" ", "_");
                    if (SEGMENT_RATES.containsKey(key)) {
                        BigDecimal boost = SEGMENT_RATES.get(key);
                        if (boost.compareTo(BigDecimal.ZERO) > 0) {
                            appliedSegments.put(segment, boost);
                            rawAddOn = rawAddOn.add(boost);
                        }
                    }
                }
            }
        }

        // 3. Cap Enforcement: min(rawAddOn, MAX_SEGMENT_ADDON_CAP)
        boolean isCapped = false;
        BigDecimal effectiveAddOn = rawAddOn;

        if (rawAddOn.compareTo(MAX_SEGMENT_ADDON_CAP) > 0) {
            effectiveAddOn = MAX_SEGMENT_ADDON_CAP;
            isCapped = true;
        }

        BigDecimal interestRate = baseRate.add(effectiveAddOn).setScale(2, RoundingMode.HALF_UP);

        // 4. Math Engine: Simple vs Compound Interest
        BigDecimal maturityAmount;
        String calcType = request.getCalculationType() != null 
                ? request.getCalculationType().trim().toUpperCase() 
                : "QUARTERLY";

        if ("SIMPLE".equals(calcType)) {
            maturityAmount = computeSimpleInterest(request.getPrincipalAmount(), interestRate, request.getTenure());
        } else {
            int frequencyPerYear = resolveCompoundingFrequency(calcType);
            maturityAmount = computeCompoundInterest(request.getPrincipalAmount(), interestRate, request.getTenure(), frequencyPerYear);
        }

        BigDecimal interestEarned = maturityAmount.subtract(request.getPrincipalAmount()).setScale(2, RoundingMode.HALF_UP);

        // 5. Response Builder
        EstimationResponse response = new EstimationResponse();
        response.setPrincipalAmount(request.getPrincipalAmount());
        response.setTenure(request.getTenure());
        response.setCalculationType(calcType);
        response.setBaseRate(baseRate);
        response.setAppliedSegmentAddOns(appliedSegments);
        response.setRawAddOnRate(rawAddOn);
        response.setMaxAllowedCap(MAX_SEGMENT_ADDON_CAP);
        response.setCapped(isCapped);
        response.setInterestRate(interestRate);
        response.setTotalInterestEarned(interestEarned);
        response.setMaturityAmount(maturityAmount);

        return response;
    }

    /**
     * Simple Interest: M = P + (P * r * t / 100)
     */
    private BigDecimal computeSimpleInterest(BigDecimal principal, BigDecimal rate, int months) {
        double p = principal.doubleValue();
        double r = rate.doubleValue();
        double t = months / 12.0;
        double interest = (p * r * t) / 100.0;
        return BigDecimal.valueOf(p + interest).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Compound Interest: M = P * (1 + r / (100 * n)) ^ (n * t)
     */
    private BigDecimal computeCompoundInterest(BigDecimal principal, BigDecimal rate, int months, int n) {
        double p = principal.doubleValue();
        double r = rate.doubleValue() / 100.0;
        double t = months / 12.0;
        double maturity = p * Math.pow(1.0 + (r / n), n * t);
        return BigDecimal.valueOf(maturity).setScale(2, RoundingMode.HALF_UP);
    }

    private int resolveCompoundingFrequency(String type) {
        return switch (type) {
            case "MONTHLY" -> 12;
            case "HALF_YEARLY" -> 2;
            case "YEARLY" -> 1;
            case "QUARTERLY" -> 4;
            default -> 4;
        };
    }

    private BigDecimal resolveBaseRate(int tenureMonths) {
        if (tenureMonths < 6) return new BigDecimal("4.50");
        if (tenureMonths < 12) return new BigDecimal("5.75");
        if (tenureMonths <= 24) return new BigDecimal("6.50");
        if (tenureMonths <= 36) return new BigDecimal("7.00");
        return new BigDecimal("6.75");
    }
}