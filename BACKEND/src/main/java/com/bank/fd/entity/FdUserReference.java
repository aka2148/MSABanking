package com.bank.fd.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Local FD holder reference resolved from the authenticated local user UUID. */
@Entity
@Table(name = "fd_user_ref", schema = "public")
public class FdUserReference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fur_user_id")
    private Long id;

    @Column(name = "fur_user_uuid", nullable = false, length = 64)
    private String userUuid;

    @Column(name = "fur_customer_number", length = 50)
    private String customerNumber;

    @Column(name = "fur_full_name", length = 255)
    private String fullName;

    @Column(name = "fur_user_status", length = 30)
    private String status;

    public Long getId() { return id; }
    public String getUserUuid() { return userUuid; }
    public String getCustomerNumber() { return customerNumber; }
    public String getFullName() { return fullName; }
    public String getStatus() { return status; }
    public void setUserUuid(String userUuid) { this.userUuid = userUuid; }
    public void setId(Long id) { this.id = id; }
    public void setCustomerNumber(String customerNumber) { this.customerNumber = customerNumber; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setStatus(String status) { this.status = status; }
}
