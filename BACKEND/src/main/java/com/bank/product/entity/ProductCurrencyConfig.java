package com.bank.product.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_currency_config")
public class ProductCurrencyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pcc_config_id")
    private Integer configId;

    @Column(name = "pcc_product_id", nullable = false)
    private Integer productId;

    @Column(name = "pcc_currency_id", nullable = false)
    private Integer currencyId;

    @Column(name = "pcc_is_base_currency", nullable = false)
    private Boolean isBaseCurrency = false;

    @Column(name = "pcc_wallet_enabled", nullable = false)
    private Boolean walletEnabled = false;

    @Column(name = "pcc_minimum_amount", precision = 22, scale = 8)
    private BigDecimal minimumAmount;

    @Column(name = "pcc_maximum_amount", precision = 22, scale = 8)
    private BigDecimal maximumAmount;

    @Column(name = "pcc_created_by", length = 100)
    private String createdBy;

    @Column(name = "pcc_created_date")
    private LocalDateTime createdDate;

    @Column(name = "pcc_updated_by", length = 100)
    private String updatedBy;

    @Column(name = "pcc_updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "pcc_version", length = 20)
    private String version;

    @Column(name = "pcc_uuid", length = 64)
    private String uuid;

    @Column(name = "pcc_ldbid", length = 50)
    private String ldbid;

    @Column(name = "pcc_host_ts")
    private LocalDateTime hostTs;

    @Column(name = "pcc_local_ts")
    private LocalDateTime localTs;

    @Column(name = "pcc_rule_system_id")
    private Integer ruleSystemId;

    @Column(name = "pcc_crud_value", length = 10)
    private String crudValue;

    @Column(name = "pcc_workstation_id")
    private Integer workstationId;

    public ProductCurrencyConfig() {}

    public Integer getConfigId() { return configId; }
    public void setConfigId(Integer configId) { this.configId = configId; }

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public Integer getCurrencyId() { return currencyId; }
    public void setCurrencyId(Integer currencyId) { this.currencyId = currencyId; }

    public Boolean getIsBaseCurrency() { return isBaseCurrency; }
    public void setIsBaseCurrency(Boolean isBaseCurrency) { this.isBaseCurrency = isBaseCurrency; }

    public Boolean getWalletEnabled() { return walletEnabled; }
    public void setWalletEnabled(Boolean walletEnabled) { this.walletEnabled = walletEnabled; }

    public BigDecimal getMinimumAmount() { return minimumAmount; }
    public void setMinimumAmount(BigDecimal minimumAmount) { this.minimumAmount = minimumAmount; }

    public BigDecimal getMaximumAmount() { return maximumAmount; }
    public void setMaximumAmount(BigDecimal maximumAmount) { this.maximumAmount = maximumAmount; }

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