package com.bank.product.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "role_type")
public class RoleType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rot_role_type_id")
    private Integer roleTypeId;

    @Column(name = "rot_role_code", nullable = false, length = 50)
    private String roleTypeCode;

    @Column(name = "rot_description", length = 500)
    private String description;

    @Column(name = "rot_is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "rot_created_by", length = 255)
    private String createdBy;

    @Column(name = "rot_created_date")
    private LocalDateTime createdDate;

    @Column(name = "rot_updated_by", length = 255)
    private String updatedBy;

    @Column(name = "rot_updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "rot_version", length = 50)
    private String version;

    @Column(name = "rot_uuid", length = 64)
    private String uuid;

    @Column(name = "rot_ldbid", length = 64)
    private String ldbid;

    @Column(name = "rot_host_ts")
    private LocalDateTime hostTs;

    @Column(name = "rot_local_ts")
    private LocalDateTime localTs;

    @Column(name = "rot_rule_system_id")
    private Integer ruleSystemId;

    @Column(name = "rot_crud_value", length = 10)
    private String crudValue;

    @Column(name = "rot_workstation_id")
    private Integer workstationId;

    public Integer getRoleTypeId() { return roleTypeId; }
    public void setRoleTypeId(Integer roleTypeId) { this.roleTypeId = roleTypeId; }
    public String getRoleTypeCode() { return roleTypeCode; }
    public void setRoleTypeCode(String roleTypeCode) { this.roleTypeCode = roleTypeCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(LocalDateTime updatedDate) { this.updatedDate = updatedDate; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getLdbid() { return ldbid; }
    public void setLdbid(String ldbid) { this.ldbid = ldbid; }
    public LocalDateTime getHostTs() { return hostTs; }
    public void setHostTs(LocalDateTime hostTs) { this.hostTs = hostTs; }
    public LocalDateTime getLocalTs() { return localTs; }
    public void setLocalTs(LocalDateTime localTs) { this.localTs = localTs; }
    public Integer getRuleSystemId() { return ruleSystemId; }
    public void setRuleSystemId(Integer ruleSystemId) { this.ruleSystemId = ruleSystemId; }
    public String getCrudValue() { return crudValue; }
    public void setCrudValue(String crudValue) { this.crudValue = crudValue; }
    public Integer getWorkstationId() { return workstationId; }
    public void setWorkstationId(Integer workstationId) { this.workstationId = workstationId; }
}
