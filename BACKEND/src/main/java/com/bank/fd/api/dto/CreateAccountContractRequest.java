package com.bank.fd.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Typed HTTP contract for account creation assertions.
 *
 * The lifecycle application service will resolve and persist authoritative
 * product terms in a later phase; this DTO only validates the request edge.
 */
public class CreateAccountContractRequest {

    @NotNull
    @Min(1)
    @JsonProperty("product_id")
    private Long productId;

    @NotNull
    @Min(1)
    @JsonProperty("currency_id")
    private Long currencyId;

    @Min(1)
    @JsonProperty("applied_rate_outcome_id")
    private Long appliedRateOutcomeId;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 14, fraction = 4)
    @JsonProperty("principal_amount")
    private BigDecimal principalAmount;

    @NotNull
    @Min(1)
    @JsonProperty("tenure_months")
    private Integer tenureMonths;

    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 5, fraction = 4)
    @JsonProperty("interest_rate")
    private BigDecimal interestRate;

    @JsonProperty("compounding_frequency")
    private String compoundingFrequency;

    @JsonProperty("payout_frequency")
    private String payoutFrequency;

    @JsonProperty("auto_renewal")
    private Boolean autoRenewal;

    @JsonProperty("open_date")
    private LocalDate openDate;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(Long currencyId) {
        this.currencyId = currencyId;
    }

    public Long getAppliedRateOutcomeId() {
        return appliedRateOutcomeId;
    }

    public void setAppliedRateOutcomeId(Long appliedRateOutcomeId) {
        this.appliedRateOutcomeId = appliedRateOutcomeId;
    }

    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    public void setPrincipalAmount(BigDecimal principalAmount) {
        this.principalAmount = principalAmount;
    }

    public Integer getTenureMonths() {
        return tenureMonths;
    }

    public void setTenureMonths(Integer tenureMonths) {
        this.tenureMonths = tenureMonths;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    public String getCompoundingFrequency() {
        return compoundingFrequency;
    }

    public void setCompoundingFrequency(String compoundingFrequency) {
        this.compoundingFrequency = compoundingFrequency;
    }

    public String getPayoutFrequency() {
        return payoutFrequency;
    }

    public void setPayoutFrequency(String payoutFrequency) {
        this.payoutFrequency = payoutFrequency;
    }

    public Boolean getAutoRenewal() {
        return autoRenewal;
    }

    public void setAutoRenewal(Boolean autoRenewal) {
        this.autoRenewal = autoRenewal;
    }

    public LocalDate getOpenDate() {
        return openDate;
    }

    public void setOpenDate(LocalDate openDate) {
        this.openDate = openDate;
    }
}
