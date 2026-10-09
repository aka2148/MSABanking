package com.bank.product.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CreateSweepConfigRequest {

    @NotNull
    private Integer sourceProductId;

    @NotNull
    private Integer targetProductId;

    @NotBlank
    @Size(max = 32)
    private String sweepType;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal thresholdAmount;

    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal sweepMultiple;

    @NotNull
    private Integer currencyId;

    public Integer getSourceProductId() {
        return sourceProductId;
    }

    public void setSourceProductId(Integer sourceProductId) {
        this.sourceProductId = sourceProductId;
    }

    public Integer getTargetProductId() {
        return targetProductId;
    }

    public void setTargetProductId(Integer targetProductId) {
        this.targetProductId = targetProductId;
    }

    public String getSweepType() {
        return sweepType;
    }

    public void setSweepType(String sweepType) {
        this.sweepType = sweepType;
    }

    public BigDecimal getThresholdAmount() {
        return thresholdAmount;
    }

    public void setThresholdAmount(BigDecimal thresholdAmount) {
        this.thresholdAmount = thresholdAmount;
    }

    public BigDecimal getSweepMultiple() {
        return sweepMultiple;
    }

    public void setSweepMultiple(BigDecimal sweepMultiple) {
        this.sweepMultiple = sweepMultiple;
    }

    public Integer getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(Integer currencyId) {
        this.currencyId = currencyId;
    }
}