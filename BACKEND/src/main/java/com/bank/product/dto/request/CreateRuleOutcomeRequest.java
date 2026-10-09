package com.bank.product.dto.request;

import java.math.BigDecimal;

public class CreateRuleOutcomeRequest {

    private String outcomeType;
    private BigDecimal outcomeValue;
    private String unit;
    private String compoundingBasis;
    private String payoutFrequency;
    private Integer currencyId;

    public String getOutcomeType() { return outcomeType; }
    public void setOutcomeType(String outcomeType) { this.outcomeType = outcomeType; }

    public BigDecimal getOutcomeValue() { return outcomeValue; }
    public void setOutcomeValue(BigDecimal outcomeValue) { this.outcomeValue = outcomeValue; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getCompoundingBasis() { return compoundingBasis; }
    public void setCompoundingBasis(String compoundingBasis) { this.compoundingBasis = compoundingBasis; }

    public String getPayoutFrequency() { return payoutFrequency; }
    public void setPayoutFrequency(String payoutFrequency) { this.payoutFrequency = payoutFrequency; }

    public Integer getCurrencyId() { return currencyId; }
    public void setCurrencyId(Integer currencyId) { this.currencyId = currencyId; }
}
