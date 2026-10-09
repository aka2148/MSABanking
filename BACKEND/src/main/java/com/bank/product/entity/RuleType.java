package com.bank.product.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rule_type")
public class RuleType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rut_rule_type_id")
    private Integer ruleTypeId;

    @Column(name = "rut_rule_type_code", nullable = false, length = 50)
    private String ruleTypeCode;

    @Column(name = "rut_description", length = 255)
    private String description;

    @Column(name = "rut_created_by", length = 100)
    private String createdBy;

    @Column(name = "rut_created_date")
    private LocalDateTime createdDate;

    @Column(name = "rut_updated_by", length = 100)
    private String updatedBy;

    @Column(name = "rut_updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "rut_version", length = 20)
    private String version;

    @Column(name = "rut_uuid", length = 64)
    private String uuid;

    @Column(name = "rut_ldbid", length = 50)
    private String ldbid;

    @Column(name = "rut_host_ts")
    private LocalDateTime hostTs;

    @Column(name = "rut_local_ts")
    private LocalDateTime localTs;

    @Column(name = "rut_rule_system_id")
    private Integer ruleSystemId;

    @Column(name = "rut_crud_value", length = 10)
    private String crudValue;

    @Column(name = "rut_workstation_id")
    private Integer workstationId;

    public RuleType() {
    }

    public Integer getRuleTypeId() {
        return ruleTypeId;
    }

    public void setRuleTypeId(Integer ruleTypeId) {
        this.ruleTypeId = ruleTypeId;
    }

    public String getRuleTypeCode() {
        return ruleTypeCode;
    }

    public void setRuleTypeCode(String ruleTypeCode) {
        this.ruleTypeCode = ruleTypeCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(LocalDateTime updatedDate) {
        this.updatedDate = updatedDate;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getLdbid() {
        return ldbid;
    }

    public void setLdbid(String ldbid) {
        this.ldbid = ldbid;
    }

    public LocalDateTime getHostTs() {
        return hostTs;
    }

    public void setHostTs(LocalDateTime hostTs) {
        this.hostTs = hostTs;
    }

    public LocalDateTime getLocalTs() {
        return localTs;
    }

    public void setLocalTs(LocalDateTime localTs) {
        this.localTs = localTs;
    }

    public Integer getRuleSystemId() {
        return ruleSystemId;
    }

    public void setRuleSystemId(Integer ruleSystemId) {
        this.ruleSystemId = ruleSystemId;
    }

    public String getCrudValue() {
        return crudValue;
    }

    public void setCrudValue(String crudValue) {
        this.crudValue = crudValue;
    }

    public Integer getWorkstationId() {
        return workstationId;
    }

    public void setWorkstationId(Integer workstationId) {
        this.workstationId = workstationId;
    }
}