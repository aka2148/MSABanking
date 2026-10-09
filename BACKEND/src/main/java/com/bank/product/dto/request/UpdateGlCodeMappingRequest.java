package com.bank.product.dto.request;

import jakarta.validation.constraints.Size;

public class UpdateGlCodeMappingRequest {

    private Integer outcomeId;

    @Size(max = 64)
    private String glCode;

    public Integer getOutcomeId() {
        return outcomeId;
    }

    public void setOutcomeId(Integer outcomeId) {
        this.outcomeId = outcomeId;
    }

    public String getGlCode() {
        return glCode;
    }

    public void setGlCode(String glCode) {
        this.glCode = glCode;
    }
}