package com.bank.customer.dto;

/** Request body for POST/PUT /customers/{customerId}/addresses. */
public class AddressRequest {
    private String addressType;
    private String city;
    private String postalCode;

    public String getAddressType() { return addressType; }
    public void setAddressType(String v) { addressType = v; }
    public String getCity() { return city; }
    public void setCity(String v) { city = v; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String v) { postalCode = v; }
}
