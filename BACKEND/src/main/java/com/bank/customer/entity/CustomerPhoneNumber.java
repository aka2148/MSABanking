package com.bank.customer.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "customer_phone_numbers", schema = "public")
public class CustomerPhoneNumber {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cph_phone_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cph_customer_id", nullable = false) private CustomerProfile customer;
    @Column(name = "cph_phone_number", nullable = false, length = 30) private String phoneNumber;
    @Column(name = "cph_contact_type", length = 30) private String contactType = "MOBILE";
    @Column(name = "cph_is_primary_signup", nullable = false) private boolean primarySignup = true;
    @Column(name = "cph_is_primary", nullable = false) private boolean primary = true;

    public void setCustomer(CustomerProfile customer) { this.customer = customer; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}