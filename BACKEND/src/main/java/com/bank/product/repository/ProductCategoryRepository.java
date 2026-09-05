package com.bank.product.repository;

import com.bank.product.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Integer> {
    Optional<ProductCategory> findByCategoryCode(String categoryCode);
    boolean existsByCategoryCode(String categoryCode);
    List<ProductCategory> findByParentCategoryIdIsNull();
    List<ProductCategory> findByParentCategoryId(Integer parentCategoryId);
}