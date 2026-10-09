package com.bank.customer.dto;

import jakarta.validation.constraints.NotNull;

public class CustomerCreateRequest {
    @NotNull private Long userId;
    private String customerNumber;
    private String legalEntityType;
    private String kycStatus;
    private String riskCategory;
    private String fullName;

    public Long getUserId(){return userId;} public void setUserId(Long value){userId=value;}
    public String getCustomerNumber(){return customerNumber;} public void setCustomerNumber(String value){customerNumber=value;}
    public String getLegalEntityType(){return legalEntityType;} public void setLegalEntityType(String value){legalEntityType=value;}
    public String getKycStatus(){return kycStatus;} public void setKycStatus(String value){kycStatus=value;}
    public String getRiskCategory(){return riskCategory;} public void setRiskCategory(String value){riskCategory=value;}
    public String getFullName(){return fullName;} public void setFullName(String value){fullName=value;}
}