package com.bank.product.dto.request;

import jakarta.validation.constraints.Size;

public class UpdateRoleTypeRequest {
    @Size(max = 500, message = "description must be at most 500 characters")
    private String description;

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
