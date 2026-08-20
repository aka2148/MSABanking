package com.bank.calculator.dto;

import java.math.BigDecimal;

public class EstimationResponse {
    private BigDecimal principal;
    private BigDecimal effectiveInterestRate;
    private BigDecimal totalInterest;
    private BigDecimal maturityAmount;
    private BigDecimal apy;

    // Constructors
    public EstimationResponse() {
    }

    public EstimationResponse(BigDecimal principal, BigDecimal effectiveInterestRate, BigDecimal totalInterest, BigDecimal maturityAmount, BigDecimal apy) {
        this.principal = principal;
        this.effectiveInterestRate = effectiveInterestRate;
        this.totalInterest = totalInterest;
        this.maturityAmount = maturityAmount;
        this.apy = apy;
    }

    // Getters and Setters
    public BigDecimal getPrincipal() {
        return principal;
    }

    public void setPrincipal(BigDecimal principal) {
        this.principal = principal;
    }

    public BigDecimal getEffectiveInterestRate() {
        return effectiveInterestRate;
    }

    public void setEffectiveInterestRate(BigDecimal effectiveInterestRate) {
        this.effectiveInterestRate = effectiveInterestRate;
    }

    public BigDecimal getTotalInterest() {
        return totalInterest;
    }

    public void setTotalInterest(BigDecimal totalInterest) {
        this.totalInterest = totalInterest;
    }

    public BigDecimal getMaturityAmount() {
        return maturityAmount;
    }

    public void setMaturityAmount(BigDecimal maturityAmount) {
        this.maturityAmount = maturityAmount;
    }

    public BigDecimal getApy() {
        return apy;
    }

    public void setApy(BigDecimal apy) {
        this.apy = apy;
    }

    // Custom Builder
    public static EstimationResponseBuilder builder() {
        return new EstimationResponseBuilder();
    }

    public static class EstimationResponseBuilder {
        private BigDecimal principal;
        private BigDecimal effectiveInterestRate;
        private BigDecimal totalInterest;
        private BigDecimal maturityAmount;
        private BigDecimal apy;

        public EstimationResponseBuilder principal(BigDecimal principal) {
            this.principal = principal;
            return this;
        }

        public EstimationResponseBuilder effectiveInterestRate(BigDecimal effectiveInterestRate) {
            this.effectiveInterestRate = effectiveInterestRate;
            return this;
        }

        public EstimationResponseBuilder totalInterest(BigDecimal totalInterest) {
            this.totalInterest = totalInterest;
            return this;
        }

        public EstimationResponseBuilder maturityAmount(BigDecimal maturityAmount) {
            this.maturityAmount = maturityAmount;
            return this;
        }

        public EstimationResponseBuilder apy(BigDecimal apy) {
            this.apy = apy;
            return this;
        }

        public EstimationResponse build() {
            return new EstimationResponse(principal, effectiveInterestRate, totalInterest, maturityAmount, apy);
        }
    }
}
