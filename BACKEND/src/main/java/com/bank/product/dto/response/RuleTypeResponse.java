package com.bank.product.dto.response;

import java.time.LocalDateTime;

public class RuleTypeResponse {

    private Integer ruleTypeId;
    private String ruleTypeCode;
    private String description;
    private String crudValue;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    public RuleTypeResponse() {
    }

    public Integer getRuleTypeId() {
        return ruleTypeId;
    }

    public void setRuleTypeId(Integer ruleTypeId) {
        this.ruleTypeId = ruleTypeId;
    }

    public String getRuleTypeCode() {
        return ruleTypeCode;
    }

    public void setRuleTypeCode(String ruleTypeCode) {
        this.ruleTypeCode = ruleTypeCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCrudValue() {
        return crudValue;
    }

    public void setCrudValue(String crudValue) {
        this.crudValue = crudValue;
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