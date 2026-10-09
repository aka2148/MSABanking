package com.bank.customer.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for POST/PUT /customers/{customerId}/phones. */
public class PhoneRequest {
    private String contactType;
    private String countryCode;
    @NotBlank private String phoneNumber;

    public String getContactType() { return contactType; }
    public void setContactType(String v) { contactType = v; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String v) { countryCode = v; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String v) { phoneNumber = v; }
}
