package com.bank.calculator.dto;

import java.math.BigDecimal;
import java.util.Map;

public class EstimationResponse {

    private BigDecimal principalAmount;
    private Integer tenure;
    private String calculationType;
    private BigDecimal baseRate;
    private Map<String, BigDecimal> appliedSegmentAddOns;
    private BigDecimal rawAddOnRate;
    private BigDecimal maxAllowedCap;
    private boolean isCapped;
    private BigDecimal interestRate;
    private BigDecimal totalInterestEarned;
    private BigDecimal maturityAmount;

    public EstimationResponse() {}

    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    public void setPrincipalAmount(BigDecimal principalAmount) {
        this.principalAmount = principalAmount;
    }

    public Integer getTenure() {
        return tenure;
    }

    public void setTenure(Integer tenure) {
        this.tenure = tenure;
    }

    public String getCalculationType() {
        return calculationType;
    }

    public void setCalculationType(String calculationType) {
        this.calculationType = calculationType;
    }

    public BigDecimal getBaseRate() {
        return baseRate;
    }

    public void setBaseRate(BigDecimal baseRate) {
        this.baseRate = baseRate;
    }

    public Map<String, BigDecimal> getAppliedSegmentAddOns() {
        return appliedSegmentAddOns;
    }

    public void setAppliedSegmentAddOns(Map<String, BigDecimal> appliedSegmentAddOns) {
        this.appliedSegmentAddOns = appliedSegmentAddOns;
    }

    public BigDecimal getRawAddOnRate() {
        return rawAddOnRate;
    }

    public void setRawAddOnRate(BigDecimal rawAddOnRate) {
        this.rawAddOnRate = rawAddOnRate;
    }

    public BigDecimal getMaxAllowedCap() {
        return maxAllowedCap;
    }

    public void setMaxAllowedCap(BigDecimal maxAllowedCap) {
        this.maxAllowedCap = maxAllowedCap;
    }

    public boolean isCapped() {
        return isCapped;
    }

    public void setCapped(boolean capped) {
        isCapped = capped;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    public BigDecimal getTotalInterestEarned() {
        return totalInterestEarned;
    }

    public void setTotalInterestEarned(BigDecimal totalInterestEarned) {
        this.totalInterestEarned = totalInterestEarned;
    }

    public BigDecimal getMaturityAmount() {
        return maturityAmount;
    }

    public void setMaturityAmount(BigDecimal maturityAmount) {
        this.maturityAmount = maturityAmount;
    }
}
