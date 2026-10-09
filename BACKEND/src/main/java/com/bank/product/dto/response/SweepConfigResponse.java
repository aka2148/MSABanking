package com.bank.product.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SweepConfigResponse {

    private Integer sweepId;
    private Integer sourceProductId;
    private Integer targetProductId;
    private String sweepType;
    private BigDecimal thresholdAmount;
    private BigDecimal sweepMultiple;
    private Integer currencyId;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    public SweepConfigResponse() {
    }

    public SweepConfigResponse(
            Integer sweepId,
            Integer sourceProductId,
            Integer targetProductId,
            String sweepType,
            BigDecimal thresholdAmount,
            BigDecimal sweepMultiple,
            Integer currencyId,
            LocalDateTime createdDate,
            LocalDateTime updatedDate) {

        this.sweepId = sweepId;
        this.sourceProductId = sourceProductId;
        this.targetProductId = targetProductId;
        this.sweepType = sweepType;
        this.thresholdAmount = thresholdAmount;
        this.sweepMultiple = sweepMultiple;
        this.currencyId = currencyId;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

    public Integer getSweepId() {
        return sweepId;
    }

    public void setSweepId(Integer sweepId) {
        this.sweepId = sweepId;
    }

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

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(LocalDateTime updatedDate) {
        this.updatedDate = updatedDate;
    }
}