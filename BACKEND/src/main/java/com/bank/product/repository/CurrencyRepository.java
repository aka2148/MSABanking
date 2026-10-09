package com.bank.product.repository;

import com.bank.product.entity.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurrencyRepository extends JpaRepository<Currency, Integer> {
    Optional<Currency> findByIsoCode(String isoCode);
    boolean existsByIsoCode(String isoCode);
    List<Currency> findByIsActive(Boolean isActive);
}