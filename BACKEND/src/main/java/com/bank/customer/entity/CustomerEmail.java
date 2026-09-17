package com.bank.customer.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "customer_emails", schema = "public")
public class CustomerEmail {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cem_email_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cem_customer_id", nullable = false) private CustomerProfile customer;
    @Column(name = "cem_email_address", nullable = false, length = 255) private String emailAddress;
    @Column(name = "cem_is_primary_signup", nullable = false) private boolean primarySignup = true;
    @Column(name = "cem_is_primary", nullable = false) private boolean primary = true;

    public void setCustomer(CustomerProfile customer) { this.customer = customer; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }
}