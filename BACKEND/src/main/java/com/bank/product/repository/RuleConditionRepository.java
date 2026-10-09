package com.bank.product.repository;

import com.bank.product.entity.RuleCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RuleConditionRepository extends JpaRepository<RuleCondition, Integer> {
    List<RuleCondition> findByRuleId(Integer ruleId);
    Optional<RuleCondition> findByConditionIdAndRuleId(Integer conditionId, Integer ruleId);
}