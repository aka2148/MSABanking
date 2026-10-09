package com.bank.product.dto.request;

public class UpdateRuleTypeRequest {

    private String description;
    private String crudValue;

    public UpdateRuleTypeRequest() {
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
}