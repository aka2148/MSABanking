package com.bank.customer.dto;

public class CustomerUpdateRequest {
    private String legalEntityType;
    private String kycStatus;
    private String riskCategory;
    private String fullName;
    public String getLegalEntityType(){return legalEntityType;} public void setLegalEntityType(String value){legalEntityType=value;}
    public String getKycStatus(){return kycStatus;} public void setKycStatus(String value){kycStatus=value;}
    public String getRiskCategory(){return riskCategory;} public void setRiskCategory(String value){riskCategory=value;}
    public String getFullName(){return fullName;} public void setFullName(String value){fullName=value;}
}