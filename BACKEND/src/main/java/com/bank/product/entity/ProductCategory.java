package com.bank.product.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_category")
public class ProductCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pc_category_id")
    private Integer categoryId;

    @Column(name = "pc_category_code", nullable = false, unique = true, length = 32)
    private String categoryCode;

    @Column(name = "pc_category_name", nullable = false, length = 128)
    private String categoryName;

    @Column(name = "pc_parent_category_id")
    private Integer parentCategoryId;

    @Column(name = "pc_created_by", length = 100)
    private String createdBy;

    @Column(name = "pc_created_date")
    private LocalDateTime createdDate;

    @Column(name = "pc_updated_by", length = 100)
    private String updatedBy;

    @Column(name = "pc_updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "pc_version", length = 20)
    private String version;

    @Column(name = "pc_uuid", length = 64)
    private String uuid;

    @Column(name = "pc_ldbid", length = 50)
    private String ldbid;

    @Column(name = "pc_host_ts")
    private LocalDateTime hostTs;

    @Column(name = "pc_local_ts")
    private LocalDateTime localTs;

    @Column(name = "pc_rule_system_id")
    private Integer ruleSystemId;

    @Column(name = "pc_crud_value", length = 10)
    private String crudValue;

    @Column(name = "pc_workstation_id")
    private Integer workstationId;

    public ProductCategory() {}

    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }

    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public Integer getParentCategoryId() { return parentCategoryId; }
    public void setParentCategoryId(Integer parentCategoryId) { this.parentCategoryId = parentCategoryId; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(LocalDateTime updatedDate) { this.updatedDate = updatedDate; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getLdbid() { return ldbid; }
    public void setLdbid(String ldbid) { this.ldbid = ldbid; }

    public LocalDateTime getHostTs() { return hostTs; }
    public void setHostTs(LocalDateTime hostTs) { this.hostTs = hostTs; }

    public LocalDateTime getLocalTs() { return localTs; }
    public void setLocalTs(LocalDateTime localTs) { this.localTs = localTs; }

    public Integer getRuleSystemId() { return ruleSystemId; }
    public void setRuleSystemId(Integer ruleSystemId) { this.ruleSystemId = ruleSystemId; }

    public String getCrudValue() { return crudValue; }
    public void setCrudValue(String crudValue) { this.crudValue = crudValue; }

    public Integer getWorkstationId() { return workstationId; }
    public void setWorkstationId(Integer workstationId) { this.workstationId = workstationId; }
}