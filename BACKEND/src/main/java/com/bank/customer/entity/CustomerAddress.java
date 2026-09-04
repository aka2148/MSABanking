package com.bank.customer.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "customer_addresses", schema = "public")
public class CustomerAddress {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cad_address_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cad_customer_id", nullable = false) private CustomerProfile customer;
    @Column(name = "cad_address_type", length = 30) private String addressType = "HOME";
    @Column(name = "cad_city", length = 255) private String city;
    @Column(name = "cad_is_primary", nullable = false) private boolean primary = true;

    public void setCustomer(CustomerProfile customer) { this.customer = customer; }
    public void setCity(String city) { this.city = city; }
}