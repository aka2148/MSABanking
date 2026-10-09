package com.bank.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class CreateProductRuleRequest {

    @NotNull(message = "Rule Type ID is required")
    private Integer ruleTypeId;

    @NotBlank(message = "Rule name is required")
    private String ruleName;

    private Integer priority = 1;
    private String combineLogic = "AND";
    private Boolean isActive = true;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;

    public CreateProductRuleRequest() {
    }

    public Integer getRuleTypeId() {
        return ruleTypeId;
    }

    public void setRuleTypeId(Integer ruleTypeId) {
        this.ruleTypeId = ruleTypeId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getCombineLogic() {
        return combineLogic;
    }

    public void setCombineLogic(String combineLogic) {
        this.combineLogic = combineLogic;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDateTime effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDateTime getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDateTime effectiveTo) {
        this.effectiveTo = effectiveTo;
    }
}