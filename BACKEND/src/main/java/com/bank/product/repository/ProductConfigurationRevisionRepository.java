package com.bank.product.repository;

import com.bank.product.entity.ProductConfigurationRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductConfigurationRevisionRepository extends JpaRepository<ProductConfigurationRevision, Long> {
    Optional<ProductConfigurationRevision> findTopByProductIdOrderByRevisionNoDesc(Integer productId);
}
