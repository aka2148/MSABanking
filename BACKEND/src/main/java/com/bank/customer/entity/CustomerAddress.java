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
}package com.bank.customer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "customer_addresses", schema = "public")
public class CustomerAddress {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "cad_address_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cad_customer_id", nullable = false) @JdbcTypeCode(SqlTypes.INTEGER) private CustomerProfile customer;
    @Column(name = "cad_address_type") private String addressType;
    @Column(name = "cad_city") private String city;
    @Column(name = "cad_postal_code") private String postalCode;
    @Column(name = "cad_is_primary", nullable = false) private boolean primaryAddress;
    @Column(name = "cad_is_verified", nullable = false) private boolean verified;
    public Long getId(){return id;} public CustomerProfile getCustomer(){return customer;} public void setCustomer(CustomerProfile v){customer=v;} public String getAddressType(){return addressType;} public void setAddressType(String v){addressType=v;} public String getCity(){return city;} public void setCity(String v){city=v;} public String getPostalCode(){return postalCode;} public void setPostalCode(String v){postalCode=v;} public boolean isPrimaryAddress(){return primaryAddress;} public void setPrimaryAddress(boolean v){primaryAddress=v;} public boolean isVerified(){return verified;} public void setVerified(boolean v){verified=v;}
}
