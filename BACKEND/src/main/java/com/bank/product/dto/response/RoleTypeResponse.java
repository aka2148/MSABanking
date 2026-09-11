package com.bank.product.dto.response;

import java.time.LocalDateTime;

public class RoleTypeResponse {
    private Integer roleTypeId;
    private String roleTypeCode;
    private String description;
    private Boolean isActive;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    public Integer getRoleTypeId() { return roleTypeId; }
    public void setRoleTypeId(Integer roleTypeId) { this.roleTypeId = roleTypeId; }
    public String getRoleTypeCode() { return roleTypeCode; }
    public void setRoleTypeCode(String roleTypeCode) { this.roleTypeCode = roleTypeCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }
    public LocalDateTime getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(LocalDateTime updatedDate) { this.updatedDate = updatedDate; }
}
