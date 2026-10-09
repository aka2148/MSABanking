package com.bank.product.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rule_outcome")
public class RuleOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ro_outcome_id")
    private Integer roOutcomeId;

    @Column(name = "ro_rule_id", nullable = false)
    private Integer ruleId;

    @Column(name = "ro_outcome_type", nullable = false, length = 32)
    private String outcomeType;

    @Column(name = "ro_outcome_value", precision = 15, scale = 4)
    private BigDecimal outcomeValue;

    @Column(name = "ro_unit", length = 16)
    private String unit;

    @Column(name = "ro_compounding_basis", length = 32)
    private String compoundingBasis;

    @Column(name = "ro_payout_frequency", length = 32)
    private String payoutFrequency;

    @Column(name = "ro_currency_id")
    private Integer currencyId;

    @Column(name = "ro_created_by", length = 100)
    private String createdBy;

    @Column(name = "ro_created_date")
    private LocalDateTime createdDate;

    @Column(name = "ro_updated_by", length = 100)
    private String updatedBy;

    @Column(name = "ro_updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "ro_version", length = 20)
    private String version;

    @Column(name = "ro_uuid", length = 64)
    private String uuid;

    @Column(name = "ro_ldbid", length = 50)
    private String ldbid;

    @Column(name = "ro_host_ts")
    private LocalDateTime hostTs;

    @Column(name = "ro_local_ts")
    private LocalDateTime localTs;

    @Column(name = "ro_rule_system_id")
    private Integer ruleSystemId;

    @Column(name = "ro_crud_value", length = 10)
    private String crudValue;

    @Column(name = "ro_workstation_id")
    private Integer workstationId;

    public RuleOutcome() {}

    public Integer getRoOutcomeId() { return roOutcomeId; }
    public void setRoOutcomeId(Integer roOutcomeId) { this.roOutcomeId = roOutcomeId; }

    public Integer getRuleId() { return ruleId; }
    public void setRuleId(Integer ruleId) { this.ruleId = ruleId; }

    public String getOutcomeType() { return outcomeType; }
    public void setOutcomeType(String outcomeType) { this.outcomeType = outcomeType; }

    public BigDecimal getOutcomeValue() { return outcomeValue; }
    public void setOutcomeValue(BigDecimal outcomeValue) { this.outcomeValue = outcomeValue; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getCompoundingBasis() { return compoundingBasis; }
    public void setCompoundingBasis(String compoundingBasis) { this.compoundingBasis = compoundingBasis; }

    public String getPayoutFrequency() { return payoutFrequency; }
    public void setPayoutFrequency(String payoutFrequency) { this.payoutFrequency = payoutFrequency; }

    public Integer getCurrencyId() { return currencyId; }
    public void setCurrencyId(Integer currencyId) { this.currencyId = currencyId; }

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