package com.bank.product.dto.request;

import jakarta.validation.constraints.NotBlank;

public class CreateRuleConditionRequest {

    @NotBlank(message = "Condition attribute is required")
    private String conditionAttribute;

    @NotBlank(message = "Operator is required")
    private String operator;

    private String valueFrom;
    private String valueTo;
    private String valueSet;

    public CreateRuleConditionRequest() {
    }

    public String getConditionAttribute() {
        return conditionAttribute;
    }

    public void setConditionAttribute(String conditionAttribute) {
        this.conditionAttribute = conditionAttribute;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getValueFrom() {
        return valueFrom;
    }

    public void setValueFrom(String valueFrom) {
        this.valueFrom = valueFrom;
    }

    public String getValueTo() {
        return valueTo;
    }

    public void setValueTo(String valueTo) {
        this.valueTo = valueTo;
    }

    public String getValueSet() {
        return valueSet;
    }

    public void setValueSet(String valueSet) {
        this.valueSet = valueSet;
    }
}