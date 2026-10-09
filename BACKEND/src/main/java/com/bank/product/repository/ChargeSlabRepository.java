package com.bank.product.repository;

import com.bank.product.entity.ChargeSlab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ChargeSlabRepository extends JpaRepository<ChargeSlab, Integer> {

    List<ChargeSlab> findByChargeId(Integer chargeId);

    @Query("SELECT cs FROM ChargeSlab cs " +
           "WHERE cs.chargeId = :chargeId " +
           "AND cs.valueFrom <= :value " +
           "AND cs.valueTo >= :value")
    Optional<ChargeSlab> findApplicableSlab(@Param("chargeId") Integer chargeId, @Param("value") BigDecimal value);
}
