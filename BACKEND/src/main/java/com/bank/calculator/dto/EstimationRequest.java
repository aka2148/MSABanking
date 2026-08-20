package com.bank.calculator.dto;

import com.bank.customer.entity.CustomerCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class EstimationRequest {

    @NotNull
    @DecimalMin("1.00")
    private BigDecimal principal;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal baseInterestRate;

    @NotNull
    @Min(1)
    private Integer termDays;

    @NotNull
    private CustomerCategory category; // e.g. STANDARD, SENIOR_CITIZEN, EMPLOYEE

    @NotBlank
    private String compoundingFrequency; // SIMPLE, MONTHLY, QUARTERLY, YEARLY

    // Constructors
    public EstimationRequest() {
    }

    // Getters and Setters
    public BigDecimal getPrincipal() {
        return principal;
    }

    public void setPrincipal(BigDecimal principal) {
        this.principal = principal;
    }

    public BigDecimal getBaseInterestRate() {
        return baseInterestRate;
    }

    public void setBaseInterestRate(BigDecimal baseInterestRate) {
        this.baseInterestRate = baseInterestRate;
    }

    public Integer getTermDays() {
        return termDays;
    }

    public void setTermDays(Integer termDays) {
        this.termDays = termDays;
    }

    public CustomerCategory getCategory() {
        return category;
    }

    public void setCategory(CustomerCategory category) {
        this.category = category;
    }

    public String getCompoundingFrequency() {
        return compoundingFrequency;
    }

    public void setCompoundingFrequency(String compoundingFrequency) {
        this.compoundingFrequency = compoundingFrequency;
    }
}
