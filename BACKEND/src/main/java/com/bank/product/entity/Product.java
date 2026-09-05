package com.bank.product.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pro_product_id")
    private Integer productId;

    @Column(name = "pro_product_code", nullable = false, unique = true, length = 32)
    private String productCode;

    @Column(name = "pro_product_name", nullable = false, length = 256)
    private String productName;

    @Column(name = "pro_category_id", nullable = false)
    private Integer categoryId;

    @Column(name = "pro_base_currency_code", length = 3)
    private String baseCurrencyCode;

    @Column(name = "pro_status", nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(name = "pro_effective_from", length = 32)
    private String effectiveFrom;

    @Column(name = "pro_effective_to", length = 32)
    private String effectiveTo;

    @Column(name = "pro_description", length = 1024)
    private String description;

    @Column(name = "pro_created_by", length = 100)
    private String createdBy;

    @Column(name = "pro_created_date")
    private LocalDateTime createdDate;

    @Column(name = "pro_updated_by", length = 100)
    private String updatedBy;

    @Column(name = "pro_updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "pro_version", length = 20)
    private String version;

    @Column(name = "pro_uuid", length = 64)
    private String uuid;

    @Column(name = "pro_crud_value", length = 10)
    private String crudValue;

    public Product() {}

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }

    public String getBaseCurrencyCode() { return baseCurrencyCode; }
    public void setBaseCurrencyCode(String baseCurrencyCode) { this.baseCurrencyCode = baseCurrencyCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public String getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

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

    public String getCrudValue() { return crudValue; }
    public void setCrudValue(String crudValue) { this.crudValue = crudValue; }
}