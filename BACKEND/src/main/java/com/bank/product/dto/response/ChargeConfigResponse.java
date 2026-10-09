package com.bank.product.dto.response;

public class ChargeConfigResponse {

    private Integer chargeId;
    private String chargeCode;
    private String calculationBasis;
    private String glCode;
    private Boolean isActive;

    public Integer getChargeId() { return chargeId; }
    public void setChargeId(Integer chargeId) { this.chargeId = chargeId; }

    public String getChargeCode() { return chargeCode; }
    public void setChargeCode(String chargeCode) { this.chargeCode = chargeCode; }

    public String getCalculationBasis() { return calculationBasis; }
    public void setCalculationBasis(String calculationBasis) { this.calculationBasis = calculationBasis; }

    public String getGlCode() { return glCode; }
    public void setGlCode(String glCode) { this.glCode = glCode; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
