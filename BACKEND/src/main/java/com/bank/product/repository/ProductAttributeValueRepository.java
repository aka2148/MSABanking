package com.bank.product.repository;
import com.bank.product.entity.ProductAttributeValue;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue,Integer>{List<ProductAttributeValue> findByProductId(Integer productId);Optional<ProductAttributeValue> findByProductIdAndAttributeDefId(Integer productId,Integer attributeDefId);boolean existsByProductIdAndAttributeDefId(Integer productId,Integer attributeDefId);boolean existsByAttributeDefId(Integer attributeDefId);}
