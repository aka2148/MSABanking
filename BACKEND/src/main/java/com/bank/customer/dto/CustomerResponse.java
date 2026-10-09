package com.bank.customer.dto;
public class CustomerResponse { private Long id; private String customerNumber,username,fullName,kycStatus,riskCategory,legalEntityType;
 public CustomerResponse(){} public CustomerResponse(Long id,String number,String username,String fullName,String kyc,String risk,String legal){this.id=id;customerNumber=number;this.username=username;this.fullName=fullName;kycStatus=kyc;riskCategory=risk;legalEntityType=legal;}
 public Long getId(){return id;} public String getCustomerNumber(){return customerNumber;} public String getUsername(){return username;} public String getFullName(){return fullName;} public String getKycStatus(){return kycStatus;} public String getRiskCategory(){return riskCategory;} public String getLegalEntityType(){return legalEntityType;}
}