package com.bank.product.dto.response;

import java.time.LocalDateTime;

public class GlCodeMappingResponse {

    private Integer mappingId;
    private Integer outcomeId;
    private String glCode;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    public GlCodeMappingResponse() {
    }

    public GlCodeMappingResponse(
            Integer mappingId,
            Integer outcomeId,
            String glCode,
            LocalDateTime createdDate,
            LocalDateTime updatedDate) {

        this.mappingId = mappingId;
        this.outcomeId = outcomeId;
        this.glCode = glCode;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

    public Integer getMappingId() {
        return mappingId;
    }

    public void setMappingId(Integer mappingId) {
        this.mappingId = mappingId;
    }

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