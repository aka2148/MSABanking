package com.bank.product.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Local FD projection of the upstream product service, stored in fd_product_ref. */
@Entity
@Table(name = "fd_product_ref", schema = "public")
public class FdProduct {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "fpr_product_id") private Long id;
    @Column(name = "fpr_product_code", nullable = false, length = 50) private String productCode;
    @Column(name = "fpr_product_name", nullable = false, length = 255) private String productName;
    @Column(name = "fpr_status", length = 30) private String status;

    /* Product rules and attributes now own these values in the upstream product schema. */
    @Transient private BigDecimal minAmount;
    @Transient private BigDecimal maxAmount;
    @Transient private Integer minTermDays;
    @Transient private Integer maxTermDays;
    @Transient private BigDecimal baseInterestRate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getMinAmount() { return minAmount; }
    public void setMinAmount(BigDecimal minAmount) { this.minAmount = minAmount; }
    public BigDecimal getMaxAmount() { return maxAmount; }
    public void setMaxAmount(BigDecimal maxAmount) { this.maxAmount = maxAmount; }
    public Integer getMinTermDays() { return minTermDays; }
    public void setMinTermDays(Integer minTermDays) { this.minTermDays = minTermDays; }
    public Integer getMaxTermDays() { return maxTermDays; }
    public void setMaxTermDays(Integer maxTermDays) { this.maxTermDays = maxTermDays; }
    public BigDecimal getBaseInterestRate() { return baseInterestRate; }
    public void setBaseInterestRate(BigDecimal baseInterestRate) { this.baseInterestRate = baseInterestRate; }
    public boolean isActive() { return "ACTIVE".equalsIgnoreCase(status); }
    public void setActive(boolean active) { status = active ? "ACTIVE" : "INACTIVE"; }
}
