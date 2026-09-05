package com.bank.product.repository;

import com.bank.product.entity.ProductCurrencyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductCurrencyConfigRepository extends JpaRepository<ProductCurrencyConfig, Integer> {
    List<ProductCurrencyConfig> findByProductId(Integer productId);
    Optional<ProductCurrencyConfig> findByProductIdAndCurrencyId(Integer productId, Integer currencyId);
    boolean existsByProductIdAndCurrencyId(Integer productId, Integer currencyId);
    boolean existsByCurrencyId(Integer currencyId);
    boolean existsByProductId(Integer productId);
}