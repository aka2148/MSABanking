package com.bank.fd.repository;

import com.bank.fd.entity.FdProductReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FdProductReferenceRepository extends JpaRepository<FdProductReference, Long> {
    Optional<FdProductReference> findByProductCode(String productCode);
}
