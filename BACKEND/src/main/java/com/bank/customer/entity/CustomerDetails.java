package com.bank.customer.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "customer_details", schema = "public")
public class CustomerDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cud_detail_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cud_customer_id", nullable = false)
    private CustomerProfile customer;

    @Column(name = "cud_full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "cud_date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "cud_father_or_spouse_name", length = 255)
    private String fatherOrSpouseName;

    @Column(name = "cud_occupation", length = 255)
    private String occupation;

    public Long getId() { return id; }
    public CustomerProfile getCustomer() { return customer; }
    public void setCustomer(CustomerProfile customer) { this.customer = customer; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getFatherOrSpouseName() { return fatherOrSpouseName; }
    public void setFatherOrSpouseName(String fatherOrSpouseName) { this.fatherOrSpouseName = fatherOrSpouseName; }
    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }
}
