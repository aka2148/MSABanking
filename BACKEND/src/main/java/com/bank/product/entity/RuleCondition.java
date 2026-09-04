package com.bank.product.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rule_condition")
public class RuleCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rc_condition_id")
    private Integer conditionId;

    @Column(name = "rc_rule_id", nullable = false)
    private Integer ruleId;

    @Column(name = "rc_condition_attribute", nullable = false, length = 100)
    private String conditionAttribute;

    @Column(name = "rc_operator", nullable = false, length = 30)
    private String operator;

    @Column(name = "rc_value_from", length = 100)
    private String valueFrom;

    @Column(name = "rc_value_to", length = 100)
    private String valueTo;

    @Column(name = "rc_value_set", length = 255)
    private String valueSet;

    @Column(name = "rc_created_by", length = 100)
    private String createdBy;

    @Column(name = "rc_created_date")
    private LocalDateTime createdDate;

    @Column(name = "rc_updated_by", length = 100)
    private String updatedBy;

    @Column(name = "rc_updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "rc_version", length = 20)
    private String version;

    @Column(name = "rc_uuid", length = 64)
    private String uuid;

    @Column(name = "rc_ldbid", length = 50)
    private String ldbid;

    @Column(name = "rc_host_ts")
    private LocalDateTime hostTs;

    @Column(name = "rc_local_ts")
    private LocalDateTime localTs;

    @Column(name = "rc_rule_system_id")
    private Integer ruleSystemId;

    @Column(name = "rc_crud_value", length = 10)
    private String crudValue;

    @Column(name = "rc_workstation_id")
    private Integer workstationId;

    public RuleCondition() {
    }

    public Integer getConditionId() {
        return conditionId;
    }

    public void setConditionId(Integer conditionId) {
        this.conditionId = conditionId;
    }

    public Integer getRuleId() {
        return ruleId;
    }

    public void setRuleId(Integer ruleId) {
        this.ruleId = ruleId;
    }

    public String getConditionAttribute() {
        return conditionAttribute;
    }

    public void setConditionAttribute(String conditionAttribute) {
        this.conditionAttribute = conditionAttribute;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getValueFrom() {
        return valueFrom;
    }

    public void setValueFrom(String valueFrom) {
        this.valueFrom = valueFrom;
    }

    public String getValueTo() {
        return valueTo;
    }

    public void setValueTo(String valueTo) {
        this.valueTo = valueTo;
    }

    public String getValueSet() {
        return valueSet;
    }

    public void setValueSet(String valueSet) {
        this.valueSet = valueSet;
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