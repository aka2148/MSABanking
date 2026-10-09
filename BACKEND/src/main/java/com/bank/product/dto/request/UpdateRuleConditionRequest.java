package com.bank.product.dto.request;

public class UpdateRuleConditionRequest {

    private String conditionAttribute;
    private String operator;
    private String valueFrom;
    private String valueTo;
    private String valueSet;

    public UpdateRuleConditionRequest() {
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