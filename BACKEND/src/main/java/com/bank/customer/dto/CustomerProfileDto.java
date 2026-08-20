package com.bank.customer.dto;

import com.bank.customer.entity.CustomerCategory;

public class CustomerProfileDto {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String address;
    private CustomerCategory category;

    // Constructors
    public CustomerProfileDto() {
    }

    public CustomerProfileDto(Long id, String username, String email, String firstName, String lastName, String phone, String address, CustomerCategory category) {
        this.id = id;
        this.username = username;
        this.email = email;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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
    public static CustomerProfileDtoBuilder builder() {
        return new CustomerProfileDtoBuilder();
    }

    public static class CustomerProfileDtoBuilder {
        private Long id;
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private String phone;
        private String address;
        private CustomerCategory category;

        public CustomerProfileDtoBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public CustomerProfileDtoBuilder username(String username) {
            this.username = username;
            return this;
        }

        public CustomerProfileDtoBuilder email(String email) {
            this.email = email;
            return this;
        }

        public CustomerProfileDtoBuilder firstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public CustomerProfileDtoBuilder lastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public CustomerProfileDtoBuilder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public CustomerProfileDtoBuilder address(String address) {
            this.address = address;
            return this;
        }

        public CustomerProfileDtoBuilder category(CustomerCategory category) {
            this.category = category;
            return this;
        }

        public CustomerProfileDto build() {
            return new CustomerProfileDto(id, username, email, firstName, lastName, phone, address, category);
        }
    }
}
