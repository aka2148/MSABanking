package com.bank.product.dto.response;

public class ProductResponse {

    private Integer productId;
    private String productCode;
    private String productName;
    private Integer categoryId;
    private String baseCurrencyCode;
    private String status;
    private String effectiveFrom;
    private String effectiveTo;
    private String description;

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
}