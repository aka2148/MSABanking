package com.bank.product.repository;

import com.bank.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {
    Optional<Product> findByProductCode(String productCode);
    boolean existsByProductCode(String productCode);
    boolean existsByProductCodeIgnoreCase(String productCode);
    List<Product> findByStatus(String status);
    List<Product> findByCategoryId(Integer categoryId);
    Optional<Product> findByProductCodeAndStatus(String productCode, String status);
}
