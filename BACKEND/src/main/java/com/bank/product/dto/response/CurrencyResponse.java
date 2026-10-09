package com.bank.product.dto.response;

public class CurrencyResponse {

    private Integer currencyId;
    private String isoCode;
    private String isoNumericCode;
    private String currencyName;
    private String currencySymbol;
    private Integer decimalPlaces;
    private Boolean isActive;

    public Integer getCurrencyId() { return currencyId; }
    public void setCurrencyId(Integer currencyId) { this.currencyId = currencyId; }

    public String getIsoCode() { return isoCode; }
    public void setIsoCode(String isoCode) { this.isoCode = isoCode; }

    public String getIsoNumericCode() { return isoNumericCode; }
    public void setIsoNumericCode(String isoNumericCode) { this.isoNumericCode = isoNumericCode; }

    public String getCurrencyName() { return currencyName; }
    public void setCurrencyName(String currencyName) { this.currencyName = currencyName; }

    public String getCurrencySymbol() { return currencySymbol; }
    public void setCurrencySymbol(String currencySymbol) { this.currencySymbol = currencySymbol; }

    public Integer getDecimalPlaces() { return decimalPlaces; }
    public void setDecimalPlaces(Integer decimalPlaces) { this.decimalPlaces = decimalPlaces; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}