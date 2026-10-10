package com.bank.product.repository;

import com.bank.product.entity.RuleOutcome;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleOutcomeRepository extends JpaRepository<RuleOutcome, Integer> {

    List<RuleOutcome> findByRuleId(Integer ruleId);
}
