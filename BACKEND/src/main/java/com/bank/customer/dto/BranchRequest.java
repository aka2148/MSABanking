package com.bank.customer.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for POST /branches and PUT /branches/{id}. */
public class BranchRequest {
    @NotBlank private String code;
    @NotBlank private String name;
    private String city;
    private Boolean active;

    public String getCode() { return code; }
    public void setCode(String v) { code = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getCity() { return city; }
    public void setCity(String v) { city = v; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean v) { active = v; }
}
