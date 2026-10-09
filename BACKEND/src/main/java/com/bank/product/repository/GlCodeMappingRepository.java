package com.bank.product.repository;

import com.bank.product.entity.GlCodeMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GlCodeMappingRepository
        extends JpaRepository<GlCodeMapping, Integer> {

    List<GlCodeMapping> findByOutcomeId(Integer outcomeId);
}