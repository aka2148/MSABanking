package com.bank.product.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_rule")
public class ProductRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pr_rule_id")
    private Integer ruleId;

    @Column(name = "pr_product_id", nullable = false)
    private Integer productId;

    @Column(name = "pr_rule_type_id", nullable = false)
    private Integer ruleTypeId;

    @Column(name = "pr_rule_name", nullable = false, length = 150)
    private String ruleName;

    @Column(name = "pr_priority")
    private Integer priority;

    @Column(name = "pr_combine_logic", length = 20)
    private String combineLogic;

    @Column(name = "pr_is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "pr_effective_from")
    private LocalDateTime effectiveFrom;

    @Column(name = "pr_effective_to")
    private LocalDateTime effectiveTo;

    @Column(name = "pr_created_by", length = 100)
    private String createdBy;

    @Column(name = "pr_created_date")
    private LocalDateTime createdDate;

    @Column(name = "pr_updated_by", length = 100)
    private String updatedBy;

    @Column(name = "pr_updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "pr_version", length = 20)
    private String version;

    @Column(name = "pr_uuid", length = 64)
    private String uuid;

    @Column(name = "pr_ldbid", length = 50)
    private String ldbid;

    @Column(name = "pr_host_ts")
    private LocalDateTime hostTs;

    @Column(name = "pr_local_ts")
    private LocalDateTime localTs;

    @Column(name = "pr_rule_system_id")
    private Integer ruleSystemId;

    @Column(name = "pr_crud_value", length = 10)
    private String crudValue;

    @Column(name = "pr_workstation_id")
    private Integer workstationId;

    public ProductRule() {
    }

    public Integer getRuleId() {
        return ruleId;
    }

    public void setRuleId(Integer ruleId) {
        this.ruleId = ruleId;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public Integer getRuleTypeId() {
        return ruleTypeId;
    }

    public void setRuleTypeId(Integer ruleTypeId) {
        this.ruleTypeId = ruleTypeId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getCombineLogic() {
        return combineLogic;
    }

    public void setCombineLogic(String combineLogic) {
        this.combineLogic = combineLogic;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDateTime effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDateTime getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDateTime effectiveTo) {
        this.effectiveTo = effectiveTo;
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