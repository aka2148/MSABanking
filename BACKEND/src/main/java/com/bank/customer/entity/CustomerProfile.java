package com.bank.customer.entity;

import jakarta.persistence.*;

/** Maps public.customers; demographic/contact data is stored in related customer tables. */
@Entity
@Table(name = "customers", schema = "public")
public class CustomerProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "cus_customer_id") private Long id;
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL) @JoinColumn(name = "cus_user_id", nullable = false, unique = true) private User user;
    @Column(name = "cus_customer_number", nullable = false, unique = true, length = 50) private String customerNumber;
    @Column(name = "cus_legal_entity_type", length = 30) private String legalEntityType;
    @Column(name = "cus_home_branch_id") private Long homeBranchId;
    @Column(name = "cus_kyc_status", nullable = false, length = 30) private String kycStatus = "PENDING";
    @Column(name = "cus_risk_category", length = 30) private String riskCategory;
    @Column(name = "cus_classification_type_id") private Long classificationTypeId;
    @Column(name = "cus_uuid", nullable = false, length = 64) private String uuid;

    /* Values now belong to customer_details, customer_phone_numbers, and customer_addresses. */
    @Transient private String firstName;
    @Transient private String lastName;
    @Transient private String phone;
    @Transient private String address;
    @Transient private CustomerCategory category;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getCustomerNumber() { return customerNumber; }
    public void setCustomerNumber(String customerNumber) { this.customerNumber = customerNumber; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public CustomerCategory getCategory() { return category; }
    public void setCategory(CustomerCategory category) { this.category = category; }
    @PrePersist
    void initializeUuid() { if (uuid == null) uuid = java.util.UUID.randomUUID().toString(); }
    public static CustomerProfileBuilder builder() { return new CustomerProfileBuilder(); }
    public static class CustomerProfileBuilder {
        private final CustomerProfile profile = new CustomerProfile();
        public CustomerProfileBuilder user(User value) { profile.setUser(value); return this; }
        public CustomerProfileBuilder firstName(String value) { profile.setFirstName(value); return this; }
        public CustomerProfileBuilder lastName(String value) { profile.setLastName(value); return this; }
        public CustomerProfileBuilder phone(String value) { profile.setPhone(value); return this; }
        public CustomerProfileBuilder address(String value) { profile.setAddress(value); return this; }
        public CustomerProfileBuilder category(CustomerCategory value) { profile.setCategory(value); return this; }
        public CustomerProfile build() { return profile; }
    }
}
