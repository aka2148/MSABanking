
package com.bank.product.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "attribute_definitions",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_attribute_definition_category_key",
        columnNames = {"ad_category_id", "ad_attribute_key"}
    )
)
public class AttributeDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ad_attribute_def_id")
    private Integer id;

    @Column(name = "ad_category_id", nullable = false)
    private Integer categoryId;

    @Column(name = "ad_attribute_key", nullable = false, length = 100)
    private String attributeKey;

    @Column(name = "ad_data_type", nullable = false, length = 30)
    private String dataType;

    @Column(name = "ad_display_name", length = 255)
    private String displayName;

    @Column(name = "ad_is_required", nullable = false)
    private Boolean required = false;

    @Column(name = "ad_created_by")
    private String createdBy;

    @Column(name = "ad_created_date")
    private LocalDateTime createdDate;

    @Column(name = "ad_updated_by")
    private String updatedBy;

    @Column(name = "ad_updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "ad_uuid")
    private String uuid;

    @Column(name = "ad_crud_value")
    private String crudValue;

    public Integer getId() {
        return id;
    }

    public void setId(Integer v) {
        id = v;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer v) {
        categoryId = v;
    }

    public String getAttributeKey() {
        return attributeKey;
    }

    public void setAttributeKey(String v) {
        attributeKey = v;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String v) {
        dataType = v;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String v) {
        displayName = v;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean v) {
        required = v;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String v) {
        updatedBy = v;
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