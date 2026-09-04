package com.bank.customer.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for POST /roles and PUT /roles/{id}. */
public class RoleRequest {
    @NotBlank private String code;
    @NotBlank private String name;
    private String description;

    public String getCode() { return code; }
    public void setCode(String v) { code = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
}
