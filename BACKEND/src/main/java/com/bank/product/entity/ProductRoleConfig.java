package com.bank.product.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "product_role_config",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_product_role_config_product_role",
        columnNames = {"prc_product_id", "prc_role_type_id"}
    )
)
public class ProductRoleConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prc_id")
    private Integer id;

    @Column(name = "prc_product_id", nullable = false)
    private Integer productId;

    @Column(name = "prc_role_type_id", nullable = false)
    private Integer roleTypeId;

    @Column(name = "prc_is_mandatory", nullable = false)
    private Boolean mandatory = false;

    @Column(name = "prc_min_count")
    private Integer minCount;

    @Column(name = "prc_max_count")
    private Integer maxCount;

    @Column(name = "prc_created_by")
    private String createdBy;

    @Column(name = "prc_created_date")
    private LocalDateTime createdDate;

    @Column(name = "prc_updated_by")
    private String updatedBy;

    @Column(name = "prc_updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "prc_version")
    private String version;

    @Column(name = "prc_uuid")
    private String uuid;

    @Column(name = "prc_crud_value")
    private String crudValue;

    public Integer getId() {
        return id;
    }

    public void setId(Integer v) {
        id = v;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer v) {
        productId = v;
    }

    public Integer getRoleTypeId() {
        return roleTypeId;
    }

    public void setRoleTypeId(Integer v) {
        roleTypeId = v;
    }

    public Boolean getMandatory() {
        return mandatory;
    }

    public void setMandatory(Boolean v) {
        mandatory = v;
    }

    public Integer getMinCount() {
        return minCount;
    }

    public void setMinCount(Integer v) {
        minCount = v;
    }

    public Integer getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(Integer v) {
        maxCount = v;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String v) {
        createdBy = v;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime v) {
        createdDate = v;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String v) {
        updatedBy = v;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(LocalDateTime v) {
        updatedDate = v;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String v) {
        version = v;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String v) {
        uuid = v;
    }

    public String getCrudValue() {
        return crudValue;
    }

    public void setCrudValue(String v) {
        crudValue = v;
    }
}