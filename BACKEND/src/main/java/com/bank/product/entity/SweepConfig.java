package com.bank.product.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sweep_config")
public class SweepConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sc_sweep_id")
    private Integer sweepId;

    @Column(name = "sc_source_product_id", nullable = false)
    private Integer sourceProductId;

    @Column(name = "sc_target_product_id", nullable = false)
    private Integer targetProductId;

    @Column(name = "sc_sweep_type", nullable = false, length = 50)
    private String sweepType;

    @Column(name = "sc_threshold_amount", precision = 19, scale = 4)
    private BigDecimal thresholdAmount;

    @Column(name = "sc_sweep_multiple", precision = 19, scale = 4)
    private BigDecimal sweepMultiple;

    @Column(name = "sc_currency_id")
    private Integer currencyId;

    @Column(name = "sc_created_by", length = 100)
    private String createdBy;

    @Column(name = "sc_created_date")
    private LocalDateTime createdDate;

    @Column(name = "sc_updated_by", length = 100)
    private String updatedBy;

    @Column(name = "sc_updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "sc_version", length = 20)
    private String version;

    @Column(name = "sc_uuid", length = 64)
    private String uuid;

    @Column(name = "sc_ldbid", length = 50)
    private String ldbid;

    @Column(name = "sc_host_ts")
    private LocalDateTime hostTs;

    @Column(name = "sc_local_ts")
    private LocalDateTime localTs;

    @Column(name = "sc_rule_system_id")
    private Integer ruleSystemId;

    @Column(name = "sc_crud_value", length = 10)
    private String crudValue;

    @Column(name = "sc_operator_user_id")
    private Integer operatorUserId;

    @Column(name = "sc_workstation_id")
    private Integer workstationId;

    public SweepConfig() {
    }

    public Integer getSweepId() {
        return sweepId;
    }

    public void setSweepId(Integer sweepId) {
        this.sweepId = sweepId;
    }

    public Integer getSourceProductId() {
        return sourceProductId;
    }

    public void setSourceProductId(Integer sourceProductId) {
        this.sourceProductId = sourceProductId;
    }

    public Integer getTargetProductId() {
        return targetProductId;
    }

    public void setTargetProductId(Integer targetProductId) {
        this.targetProductId = targetProductId;
    }

    public String getSweepType() {
        return sweepType;
    }

    public void setSweepType(String sweepType) {
        this.sweepType = sweepType;
    }

    public BigDecimal getThresholdAmount() {
        return thresholdAmount;
    }

    public void setThresholdAmount(BigDecimal thresholdAmount) {
        this.thresholdAmount = thresholdAmount;
    }

    public BigDecimal getSweepMultiple() {
        return sweepMultiple;
    }

    public void setSweepMultiple(BigDecimal sweepMultiple) {
        this.sweepMultiple = sweepMultiple;
    }

    public Integer getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(Integer currencyId) {
        this.currencyId = currencyId;
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

    public Integer getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Integer operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public Integer getWorkstationId() {
        return workstationId;
    }

    public void setWorkstationId(Integer workstationId) {
        this.workstationId = workstationId;
    }
}