package com.bank.product.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "charge_slab")
public class ChargeSlab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cs_slab_id")
    private Integer slabId;

    @Column(name = "cs_charge_id", nullable = false)
    private Integer chargeId;

    @Column(name = "cs_slab_attribute", length = 32)
    private String slabAttribute;

    @Column(name = "cs_value_from", precision = 15, scale = 4)
    private BigDecimal valueFrom;

    @Column(name = "cs_value_to", precision = 15, scale = 4)
    private BigDecimal valueTo;

    @Column(name = "cs_charge_amount", precision = 15, scale = 4)
    private BigDecimal chargeAmount;

    @Column(name = "cs_currency_id")
    private Integer currencyId;

    @Column(name = "cs_created_by", length = 100)
    private String createdBy;

    @Column(name = "cs_created_date")
    private LocalDateTime createdDate;

    @Column(name = "cs_updated_by", length = 100)
    private String updatedBy;

    @Column(name = "cs_updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "cs_version", length = 20)
    private String version;

    @Column(name = "cs_uuid", length = 64)
    private String uuid;

    @Column(name = "cs_ldbid", length = 50)
    private String ldbid;

    @Column(name = "cs_host_ts")
    private LocalDateTime hostTs;

    @Column(name = "cs_local_ts")
    private LocalDateTime localTs;

    @Column(name = "cs_rule_system_id")
    private Integer ruleSystemId;

    @Column(name = "cs_crud_value", length = 10)
    private String crudValue;

    @Column(name = "cs_workstation_id")
    private Integer workstationId;

    public ChargeSlab() {}

    public Integer getSlabId() { return slabId; }
    public void setSlabId(Integer slabId) { this.slabId = slabId; }

    public Integer getChargeId() { return chargeId; }
    public void setChargeId(Integer chargeId) { this.chargeId = chargeId; }

    public String getSlabAttribute() { return slabAttribute; }
    public void setSlabAttribute(String slabAttribute) { this.slabAttribute = slabAttribute; }

    public BigDecimal getValueFrom() { return valueFrom; }
    public void setValueFrom(BigDecimal valueFrom) { this.valueFrom = valueFrom; }

    public BigDecimal getValueTo() { return valueTo; }
    public void setValueTo(BigDecimal valueTo) { this.valueTo = valueTo; }

    public BigDecimal getChargeAmount() { return chargeAmount; }
    public void setChargeAmount(BigDecimal chargeAmount) { this.chargeAmount = chargeAmount; }

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
