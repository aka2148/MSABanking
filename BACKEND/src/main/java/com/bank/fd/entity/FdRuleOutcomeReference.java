package com.bank.fd.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Local applied-rate reference identity, distinct from normalized rule outcome IDs. */
@Entity
@Table(name = "fd_rule_outcome_ref", schema = "public")
public class FdRuleOutcomeReference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fror_outcome_id")
    private Long id;

    @Column(name = "fror_outcome_type", length = 30)
    private String outcomeType;

    @Column(name = "fror_outcome_value", length = 255)
    private String outcomeValue;

    @Column(name = "fror_unit", length = 30)
    private String unit;

    @Column(name = "fror_source_outcome_id")
    private Integer sourceOutcomeId;

    public Long getId() { return id; }
    public String getOutcomeType() { return outcomeType; }
    public String getOutcomeValue() { return outcomeValue; }
    public String getUnit() { return unit; }
    public Integer getSourceOutcomeId() { return sourceOutcomeId; }
    public void setOutcomeType(String outcomeType) { this.outcomeType = outcomeType; }
    public void setOutcomeValue(String outcomeValue) { this.outcomeValue = outcomeValue; }
    public void setUnit(String unit) { this.unit = unit; }
    public void setSourceOutcomeId(Integer sourceOutcomeId) { this.sourceOutcomeId = sourceOutcomeId; }
}
