package com.bank.customer.entity;

import jakarta.persistence.*;

/** Maps public.customer_phone_numbers. */
@Entity
@Table(name = "customer_phone_numbers", schema = "public")
public class CustomerPhone {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "cph_phone_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cph_customer_id", nullable = false) private CustomerProfile customer;
    @Column(name = "cph_contact_type") private String contactType;
    @Column(name = "cph_country_code") private String countryCode;
    @Column(name = "cph_phone_number", nullable = false) private String phoneNumber;
    /** True if this was the phone number used at signup registration. */
    @Column(name = "cph_is_primary_signup", nullable = false) private boolean primarySignupPhone;
    @Column(name = "cph_is_primary", nullable = false) private boolean primaryPhone;
    @Column(name = "cph_is_verified", nullable = false) private boolean verified;
    @Column(name = "cph_verification_method") private String verificationMethod;
    @Column(name = "cph_consent_marketing", nullable = false) private boolean consentMarketing;

    public Long getId() { return id; }
    public CustomerProfile getCustomer() { return customer; }
    public void setCustomer(CustomerProfile v) { customer = v; }
    public String getContactType() { return contactType; }
    public void setContactType(String v) { contactType = v; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String v) { countryCode = v; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String v) { phoneNumber = v; }
    public boolean isPrimarySignupPhone() { return primarySignupPhone; }
    public void setPrimarySignupPhone(boolean v) { primarySignupPhone = v; }
    public boolean isPrimaryPhone() { return primaryPhone; }
    public void setPrimaryPhone(boolean v) { primaryPhone = v; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean v) { verified = v; }
    public String getVerificationMethod() { return verificationMethod; }
    public void setVerificationMethod(String v) { verificationMethod = v; }
    public boolean isConsentMarketing() { return consentMarketing; }
    public void setConsentMarketing(boolean v) { consentMarketing = v; }
}