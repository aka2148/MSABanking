package com.bank.customer.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "customer_profiles")
public class CustomerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String phone;

    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerCategory category;

    // Constructors
    public CustomerProfile() {
    }

    public CustomerProfile(Long id, User user, String firstName, String lastName, String phone, String address, CustomerCategory category) {
        this.id = id;
        this.user = user;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.address = address;
        this.category = category;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public CustomerCategory getCategory() {
        return category;
    }

    public void setCategory(CustomerCategory category) {
        this.category = category;
    }

    // Custom Builder
    public static CustomerProfileBuilder builder() {
        return new CustomerProfileBuilder();
    }

    public static class CustomerProfileBuilder {
        private Long id;
        private User user;
        private String firstName;
        private String lastName;
        private String phone;
        private String address;
        private CustomerCategory category;

        public CustomerProfileBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public CustomerProfileBuilder user(User user) {
            this.user = user;
            return this;
        }

        public CustomerProfileBuilder firstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public CustomerProfileBuilder lastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public CustomerProfileBuilder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public CustomerProfileBuilder address(String address) {
            this.address = address;
            return this;
        }

        public CustomerProfileBuilder category(CustomerCategory category) {
            this.category = category;
            return this;
        }

        public CustomerProfile build() {
            return new CustomerProfile(id, user, firstName, lastName, phone, address, category);
        }
    }
}
