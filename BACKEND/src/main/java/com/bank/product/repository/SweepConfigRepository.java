package com.bank.product.repository;

import com.bank.product.entity.SweepConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SweepConfigRepository
        extends JpaRepository<SweepConfig, Integer> {

    List<SweepConfig> findBySourceProductId(Integer sourceProductId);

    List<SweepConfig> findByTargetProductId(Integer targetProductId);

    List<SweepConfig> findByCurrencyId(Integer currencyId);
}