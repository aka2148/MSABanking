package com.bank.fd.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Local reference identity used by fd_accounts, distinct from normalized product IDs. */
@Entity
@Table(name = "fd_product_ref", schema = "public")
public class FdProductReference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fpr_product_id")
    private Long id;

    @Column(name = "fpr_product_code", nullable = false, length = 50)
    private String productCode;

    @Column(name = "fpr_product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "fpr_status", length = 30)
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
