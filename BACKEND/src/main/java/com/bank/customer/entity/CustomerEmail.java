package com.bank.customer.entity;

import jakarta.persistence.*;

/** Maps public.customer_emails. */
@Entity
@Table(name = "customer_emails", schema = "public")
public class CustomerEmail {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "cem_email_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cem_customer_id", nullable = false) private CustomerProfile customer;
    @Column(name = "cem_contact_type") private String contactType;
    @Column(name = "cem_email_address", nullable = false) private String emailAddress;
    /** True if this was the email address used at signup registration. */
    @Column(name = "cem_is_primary_signup", nullable = false) private boolean primarySignupEmail;
    @Column(name = "cem_is_primary", nullable = false) private boolean primaryEmail;
    @Column(name = "cem_is_verified", nullable = false) private boolean verified;
    @Column(name = "cem_verification_method") private String verificationMethod;
    @Column(name = "cem_consent_marketing", nullable = false) private boolean consentMarketing;

    public Long getId() { return id; }
    public CustomerProfile getCustomer() { return customer; }
    public void setCustomer(CustomerProfile v) { customer = v; }
    public String getContactType() { return contactType; }
    public void setContactType(String v) { contactType = v; }
    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String v) { emailAddress = v; }
    public boolean isPrimarySignupEmail() { return primarySignupEmail; }
    public void setPrimarySignupEmail(boolean v) { primarySignupEmail = v; }
    public boolean isPrimaryEmail() { return primaryEmail; }
    public void setPrimaryEmail(boolean v) { primaryEmail = v; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean v) { verified = v; }
    public String getVerificationMethod() { return verificationMethod; }
    public void setVerificationMethod(String v) { verificationMethod = v; }
    public boolean isConsentMarketing() { return consentMarketing; }
    public void setConsentMarketing(boolean v) { consentMarketing = v; }
}