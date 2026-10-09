package com.bank.product.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "charge_config")
public class ChargeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cc_charge_id")
    private Integer chargeId;

    @Column(name = "cc_product_id", nullable = false)
    private Integer productId;

    @Column(name = "cc_charge_code", length = 32)
    private String chargeCode;

    @Column(name = "cc_calculation_basis", length = 32)
    private String calculationBasis;

    @Column(name = "cc_gl_code", length = 32)
    private String glCode;

    @Column(name = "cc_is_active")
    private Boolean isActive;

    @Column(name = "cc_created_by", length = 100)
    private String createdBy;

    @Column(name = "cc_created_date")
    private LocalDateTime createdDate;

    @Column(name = "cc_updated_by", length = 100)
    private String updatedBy;

    @Column(name = "cc_updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "cc_version", length = 20)
    private String version;

    @Column(name = "cc_uuid", length = 64)
    private String uuid;

    @Column(name = "cc_ldbid", length = 50)
    private String ldbid;

    @Column(name = "cc_host_ts")
    private LocalDateTime hostTs;

    @Column(name = "cc_local_ts")
    private LocalDateTime localTs;

    @Column(name = "cc_rule_system_id")
    private Integer ruleSystemId;

    @Column(name = "cc_crud_value", length = 10)
    private String crudValue;

    @Column(name = "cc_workstation_id")
    private Integer workstationId;

    public ChargeConfig() {}

    public Integer getChargeId() { return chargeId; }
    public void setChargeId(Integer chargeId) { this.chargeId = chargeId; }

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public String getChargeCode() { return chargeCode; }
    public void setChargeCode(String chargeCode) { this.chargeCode = chargeCode; }

    public String getCalculationBasis() { return calculationBasis; }
    public void setCalculationBasis(String calculationBasis) { this.calculationBasis = calculationBasis; }

    public String getGlCode() { return glCode; }
    public void setGlCode(String glCode) { this.glCode = glCode; }

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