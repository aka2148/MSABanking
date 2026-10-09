package com.bank.product.dto.request;

import jakarta.validation.constraints.NotBlank;

public class CreateRuleTypeRequest {

    @NotBlank(message = "Rule type code is required")
    private String ruleTypeCode;

    private String description;

    public CreateRuleTypeRequest() {
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
}