package com.bank.product.dto.response;

public class ProductCurrencyConfigResponse {

    private Integer configId;
    private Integer productId;
    private Integer currencyId;
    private Boolean isBaseCurrency;
    private Boolean walletEnabled;

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
}