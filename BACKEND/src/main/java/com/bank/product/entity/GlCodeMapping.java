package com.bank.product.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Maps a rule outcome to the General Ledger account code
 * that accounting entries should post to.
 *
 * Table: gl_code_mapping
 * Prefix: gcm
 */
@Entity
@Table(name = "gl_code_mapping")
public class GlCodeMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "gcm_mapping_id")
    private Integer mappingId;

    @Column(name = "gcm_outcome_id", nullable = false)
    private Integer outcomeId;

    @Column(name = "gcm_gl_code", nullable = false, length = 50)
    private String glCode;

    @Column(name = "gcm_created_by", length = 100)
    private String createdBy;

    @Column(name = "gcm_created_date")
    private LocalDateTime createdDate;

    @Column(name = "gcm_updated_by", length = 100)
    private String updatedBy;

    @Column(name = "gcm_updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "gcm_version", length = 20)
    private String version;

    @Column(name = "gcm_uuid", length = 64)
    private String uuid;

    @Column(name = "gcm_ldbid", length = 50)
    private String ldbid;

    @Column(name = "gcm_host_ts")
    private LocalDateTime hostTs;

    @Column(name = "gcm_local_ts")
    private LocalDateTime localTs;

    @Column(name = "gcm_rule_system_id")
    private Integer ruleSystemId;

    @Column(name = "gcm_crud_value", length = 10)
    private String crudValue;

    @Column(name = "gcm_operator_user_id")
    private Integer operatorUserId;

    @Column(name = "gcm_workstation_id")
    private Integer workstationId;

    public GlCodeMapping() {
    }

    public Integer getMappingId() {
        return mappingId;
    }

    public void setMappingId(Integer mappingId) {
        this.mappingId = mappingId;
    }

    public Integer getOutcomeId() {
        return outcomeId;
    }

    public void setOutcomeId(Integer outcomeId) {
        this.outcomeId = outcomeId;
    }

    public String getGlCode() {
        return glCode;
    }

    public void setGlCode(String glCode) {
        this.glCode = glCode;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(LocalDateTime updatedDate) {
        this.updatedDate = updatedDate;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getLdbid() {
        return ldbid;
    }

    public void setLdbid(String ldbid) {
        this.ldbid = ldbid;
    }

    public LocalDateTime getHostTs() {
        return hostTs;
    }

    public void setHostTs(LocalDateTime hostTs) {
        this.hostTs = hostTs;
    }

    public LocalDateTime getLocalTs() {
        return localTs;
    }

    public void setLocalTs(LocalDateTime localTs) {
        this.localTs = localTs;
    }

    public Integer getRuleSystemId() {
        return ruleSystemId;
    }

    public void setRuleSystemId(Integer ruleSystemId) {
        this.ruleSystemId = ruleSystemId;
    }

    public String getCrudValue() {
        return crudValue;
    }

    public void setCrudValue(String crudValue) {
        this.crudValue = crudValue;
    }

    public Integer getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Integer operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public Integer getWorkstationId() {
        return workstationId;
    }

    public void setWorkstationId(Integer workstationId) {
        this.workstationId = workstationId;
    }
}