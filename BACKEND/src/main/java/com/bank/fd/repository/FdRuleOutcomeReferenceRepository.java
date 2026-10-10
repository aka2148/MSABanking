package com.bank.fd.repository;

import com.bank.fd.entity.FdRuleOutcomeReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FdRuleOutcomeReferenceRepository extends JpaRepository<FdRuleOutcomeReference, Long> {
    List<FdRuleOutcomeReference> findByOutcomeTypeAndOutcomeValue(String outcomeType, String outcomeValue);
    java.util.Optional<FdRuleOutcomeReference> findBySourceOutcomeId(Integer sourceOutcomeId);
}
