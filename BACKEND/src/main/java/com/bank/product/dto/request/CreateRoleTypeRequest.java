package com.bank.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateRoleTypeRequest {
    @NotBlank(message = "roleTypeCode is required")
    @Size(max = 50, message = "roleTypeCode must be at most 50 characters")
    private String roleTypeCode;

    @Size(max = 500, message = "description must be at most 500 characters")
    private String description;

    public String getRoleTypeCode() { return roleTypeCode; }
    public void setRoleTypeCode(String roleTypeCode) { this.roleTypeCode = roleTypeCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
