package com.bank.product.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_repayment_schedule")
public class LoanRepaymentSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lrs_schedule_id")
    private Integer scheduleId;

    @Column(name = "lrs_loan_account_no", nullable = false, length = 50)
    private String loanAccountNo;

    @Column(name = "lrs_product_id", nullable = false)
    private Integer productId;

    @Column(name = "lrs_note", length = 1000)
    private String note;

    @Column(name = "lrs_created_by", length = 100)
    private String createdBy;

    @Column(name = "lrs_created_date")
    private LocalDateTime createdDate;

    @Column(name = "lrs_updated_by", length = 100)
    private String updatedBy;

    @Column(name = "lrs_updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "lrs_version", length = 20)
    private String version;

    @Column(name = "lrs_uuid", length = 64)
    private String uuid;

    @Column(name = "lrs_ldbid", length = 50)
    private String ldbid;

    @Column(name = "lrs_host_ts")
    private LocalDateTime hostTs;

    @Column(name = "lrs_local_ts")
    private LocalDateTime localTs;

    @Column(name = "lrs_rule_system_id")
    private Integer ruleSystemId;

    @Column(name = "lrs_crud_value", length = 10)
    private String crudValue;

    @Column(name = "lrs_operator_user_id")
    private Integer operatorUserId;

    @Column(name = "lrs_workstation_id")
    private Integer workstationId;

    public LoanRepaymentSchedule() {
    }

    public Integer getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(Integer scheduleId) {
        this.scheduleId = scheduleId;
    }

    public String getLoanAccountNo() {
        return loanAccountNo;
    }

    public void setLoanAccountNo(String loanAccountNo) {
        this.loanAccountNo = loanAccountNo;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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