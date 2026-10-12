
package com.bank.product.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
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

    @Column(name = "ad_choice_values", length = 1000)
    private String choiceValues;

    @Column(name = "ad_unit", length = 30)
    private String unit;

    @Column(name = "ad_default_value", length = 255)
    private String defaultValue;

    @Column(name = "ad_min_value", precision = 22, scale = 8)
    private BigDecimal minValue;

    @Column(name = "ad_max_value", precision = 22, scale = 8)
    private BigDecimal maxValue;

    @Column(name = "ad_display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "ad_behavior_key", length = 50)
    private String behaviorKey;

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

    public String getChoiceValues() { return choiceValues; }
    public void setChoiceValues(String value) { choiceValues = value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { unit = value; }
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String value) { defaultValue = value; }
    public BigDecimal getMinValue() { return minValue; }
    public void setMinValue(BigDecimal value) { minValue = value; }
    public BigDecimal getMaxValue() { return maxValue; }
    public void setMaxValue(BigDecimal value) { maxValue = value; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer value) { displayOrder = value; }
    public String getBehaviorKey() { return behaviorKey; }
    public void setBehaviorKey(String value) { behaviorKey = value; }
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