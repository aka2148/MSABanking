package com.bank.fd.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Maps public.fd_premature_closure from the BankingTech PostgreSQL schema. */
@Entity
@Table(name = "fd_premature_closure", schema = "public")
public class FdPrematureClosure {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "fpc_closure_id") private Long id;
    @Column(name = "fpc_account_id", nullable = false) private Long accountId;
    @Column(name = "fpc_closure_date", nullable = false) private OffsetDateTime closureDate;
    @Column(name = "fpc_penalty_pct_applied", precision = 9, scale = 4) private BigDecimal penaltyPctApplied;
    @Column(name = "fpc_penalty_amount", precision = 18, scale = 4) private BigDecimal penaltyAmount;
    @Column(name = "fpc_net_payable_amount", precision = 18, scale = 4) private BigDecimal netPayableAmount;
    @Column(name = "fpc_currency_id", nullable = false) private Long currencyId;
    @Column(name = "fpc_reason", length = 500) private String reason;
    @Column(name = "fpc_applied_rule_outcome_id") private Long appliedRuleOutcomeId;
    @Column(name = "fpc_approved_by") private Long approvedBy;
    @Column(name = "fpc_status", nullable = false, length = 20) private String status = "PENDING";
    @Column(name = "fpc_uuid", nullable = false, length = 64) private String uuid;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public OffsetDateTime getClosureDate() { return closureDate; }
    public void setClosureDate(OffsetDateTime closureDate) { this.closureDate = closureDate; }
    public BigDecimal getPenaltyPctApplied() { return penaltyPctApplied; }
    public void setPenaltyPctApplied(BigDecimal penaltyPctApplied) { this.penaltyPctApplied = penaltyPctApplied; }
    public BigDecimal getPenaltyAmount() { return penaltyAmount; }
    public void setPenaltyAmount(BigDecimal penaltyAmount) { this.penaltyAmount = penaltyAmount; }
    public BigDecimal getNetPayableAmount() { return netPayableAmount; }
    public void setNetPayableAmount(BigDecimal netPayableAmount) { this.netPayableAmount = netPayableAmount; }
    public Long getCurrencyId() { return currencyId; }
    public void setCurrencyId(Long currencyId) { this.currencyId = currencyId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getAppliedRuleOutcomeId() { return appliedRuleOutcomeId; }
    public void setAppliedRuleOutcomeId(Long appliedRuleOutcomeId) { this.appliedRuleOutcomeId = appliedRuleOutcomeId; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    @PrePersist
    void initializeUuid() { if (uuid == null) uuid = java.util.UUID.randomUUID().toString(); }
}
