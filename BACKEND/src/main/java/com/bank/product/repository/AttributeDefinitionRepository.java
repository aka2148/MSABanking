package com.bank.product.repository;

import com.bank.product.entity.AttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttributeDefinitionRepository
        extends JpaRepository<AttributeDefinition, Integer> {

    List<AttributeDefinition> findByCategoryId(Integer categoryId);

    boolean existsByCategoryIdAndAttributeKey(
            Integer categoryId,
            String attributeKey
    );
}