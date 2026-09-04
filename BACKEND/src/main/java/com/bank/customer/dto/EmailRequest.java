package com.bank.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request body for POST/PUT /customers/{customerId}/emails. */
public class EmailRequest {
    private String contactType;
    @NotBlank @Email private String emailAddress;

    public String getContactType() { return contactType; }
    public void setContactType(String v) { contactType = v; }
    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String v) { emailAddress = v; }
}
