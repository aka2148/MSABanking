package com.bank.product.repository;

import com.bank.product.entity.ProductRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRuleRepository extends JpaRepository<ProductRule, Integer> {
    List<ProductRule> findByProductId(Integer productId);
    Optional<ProductRule> findByRuleIdAndProductId(Integer ruleId, Integer productId);
    List<ProductRule> findByProductIdAndRuleNameAndIsActiveTrue(Integer productId, String ruleName);
}
