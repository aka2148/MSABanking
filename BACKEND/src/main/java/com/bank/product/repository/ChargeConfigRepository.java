package com.bank.product.repository;

import com.bank.product.entity.ChargeConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChargeConfigRepository extends JpaRepository<ChargeConfig, Integer> {

    List<ChargeConfig> findByProductId(Integer productId);

    List<ChargeConfig> findByProductIdAndIsActive(Integer productId, Boolean isActive);
}
