package com.bank.calculator.service;

import com.bank.calculator.dto.EstimationRequest;
import com.bank.calculator.dto.EstimationResponse;
import com.bank.customer.entity.CustomerCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FdCalculatorServiceTest {

    private final FdCalculatorService fdCalculatorService = new FdCalculatorService();

    @Test
    public void testCalculateSimpleInterest_Standard() {
        // Principal: 10,000, Base Rate: 6.0%, Days: 365, Simple Interest
        EstimationRequest request = new EstimationRequest();
        request.setPrincipal(new BigDecimal("10000.00"));
        request.setBaseInterestRate(new BigDecimal("6.00"));
        request.setTermDays(365);
        request.setCategory(CustomerCategory.STANDARD);
        request.setCompoundingFrequency("SIMPLE");

        EstimationResponse response = fdCalculatorService.calculateEstimation(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("6.00"), response.getEffectiveInterestRate());
        assertEquals(new BigDecimal("600.00"), response.getTotalInterest());
        assertEquals(new BigDecimal("10600.00"), response.getMaturityAmount());
    }

    @Test
    public void testCalculateSimpleInterest_SeniorCitizen() {
        // Principal: 10,000, Base Rate: 6.0%, Days: 365, Simple Interest
        // Senior Citizen gets +0.50% boost -> 6.50%
        EstimationRequest request = new EstimationRequest();
        request.setPrincipal(new BigDecimal("10000.00"));
        request.setBaseInterestRate(new BigDecimal("6.00"));
        request.setTermDays(365);
        request.setCategory(CustomerCategory.SENIOR_CITIZEN);
        request.setCompoundingFrequency("SIMPLE");

        EstimationResponse response = fdCalculatorService.calculateEstimation(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("6.50"), response.getEffectiveInterestRate());
        assertEquals(new BigDecimal("650.00"), response.getTotalInterest());
        assertEquals(new BigDecimal("10650.00"), response.getMaturityAmount());
    }

    @Test
    public void testCalculateCompoundInterest_Quarterly_Employee() {
        // Principal: 10,000, Base Rate: 7.25%, Days: 365, Quarterly Compound
        // Employee gets +0.75% boost -> 8.00%
        // A = 10000 * (1 + 0.08 / 4)^(4 * 1) = 10000 * (1.02)^4 = 10000 * 1.082432 = 10824.32
        EstimationRequest request = new EstimationRequest();
        request.setPrincipal(new BigDecimal("10000.00"));
        request.setBaseInterestRate(new BigDecimal("7.25"));
        request.setTermDays(365);
        request.setCategory(CustomerCategory.EMPLOYEE);
        request.setCompoundingFrequency("QUARTERLY");

        EstimationResponse response = fdCalculatorService.calculateEstimation(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("8.00"), response.getEffectiveInterestRate());
        assertEquals(new BigDecimal("824.32"), response.getTotalInterest());
        assertEquals(new BigDecimal("10824.32"), response.getMaturityAmount());
        assertEquals(new BigDecimal("8.2432"), response.getApy()); // (1.02)^4 - 1 = 8.2432%
    }

    @Test
    public void testPrematurePayoutCalculation() {
        // Principal: 10,000, Base Rate: 6.0%, Elapsed Days: 180, Simple Interest, Category: Standard
        // Penalized rate = 6.0% - 1.0% = 5.0%
        // Expected Interest = 10,000 * 0.05 * 180 / 365 = 246.5753... -> 246.58
        BigDecimal payout = fdCalculatorService.calculatePrematurePayout(
                new BigDecimal("10000.00"),
                new BigDecimal("6.00"),
                180,
                CustomerCategory.STANDARD,
                "SIMPLE"
        );

        assertEquals(new BigDecimal("10246.58"), payout);
    }
}
