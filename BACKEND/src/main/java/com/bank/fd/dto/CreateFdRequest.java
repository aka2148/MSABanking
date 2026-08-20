package com.bank.fd.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class CreateFdRequest {

    @NotNull
    private Long productId;

    @NotNull
    @DecimalMin("1.00")
    private BigDecimal depositAmount;

    @NotNull
    @Min(1)
    private Integer termDays;

    @NotBlank
    private String compoundingFrequency; // SIMPLE, MONTHLY, QUARTERLY, YEARLY

    @NotBlank
    private String branchCode; // 3-digit branch code (e.g. 001)

    // Constructors
    public CreateFdRequest() {
    }

    public CreateFdRequest(Long productId, BigDecimal depositAmount, Integer termDays, String compoundingFrequency, String branchCode) {
        this.productId = productId;
        this.depositAmount = depositAmount;
        this.termDays = termDays;
        this.compoundingFrequency = compoundingFrequency;
        this.branchCode = branchCode;
    }

    // Getters and Setters
    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(BigDecimal depositAmount) {
        this.depositAmount = depositAmount;
    }

    public Integer getTermDays() {
        return termDays;
    }

    public void setTermDays(Integer termDays) {
        this.termDays = termDays;
    }

    public String getCompoundingFrequency() {
        return compoundingFrequency;
    }

    public void setCompoundingFrequency(String compoundingFrequency) {
        this.compoundingFrequency = compoundingFrequency;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public void setBranchCode(String branchCode) {
        this.branchCode = branchCode;
    }
}
