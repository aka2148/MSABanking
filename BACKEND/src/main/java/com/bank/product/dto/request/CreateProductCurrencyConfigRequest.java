package com.bank.product.dto.request;

public class CreateProductCurrencyConfigRequest {

    private Integer currencyId;
    private Boolean isBaseCurrency;
    private Boolean walletEnabled;

    public Integer getCurrencyId() { return currencyId; }
    public void setCurrencyId(Integer currencyId) { this.currencyId = currencyId; }

    public Boolean getIsBaseCurrency() { return isBaseCurrency; }
    public void setIsBaseCurrency(Boolean isBaseCurrency) { this.isBaseCurrency = isBaseCurrency; }

    public Boolean getWalletEnabled() { return walletEnabled; }
    public void setWalletEnabled(Boolean walletEnabled) { this.walletEnabled = walletEnabled; }
}