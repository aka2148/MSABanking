package com.bank.product.dto.request;

import jakarta.validation.constraints.NotNull;

public class UpdateRoleTypeStatusRequest {
    @NotNull(message = "isActive is required")
    private Boolean isActive;

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
