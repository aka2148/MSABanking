package com.bank.fd.repository;

import com.bank.fd.entity.FdCurrencyReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FdCurrencyReferenceRepository extends JpaRepository<FdCurrencyReference, Long> {
    Optional<FdCurrencyReference> findByIsoCode(String isoCode);
}
