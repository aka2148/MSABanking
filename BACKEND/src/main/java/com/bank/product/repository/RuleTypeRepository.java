package com.bank.product.repository;

import com.bank.product.entity.RuleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RuleTypeRepository extends JpaRepository<RuleType, Integer> {
    Optional<RuleType> findByRuleTypeCode(String ruleTypeCode);
}