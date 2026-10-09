package com.bank.product.dto.response;

import java.math.BigDecimal;

public class ChargeSlabResponse {

    private Integer slabId;
    private String slabAttribute;
    private BigDecimal valueFrom;
    private BigDecimal valueTo;
    private BigDecimal chargeAmount;
    private Integer currencyId;

    public Integer getSlabId() { return slabId; }
    public void setSlabId(Integer slabId) { this.slabId = slabId; }

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
}
