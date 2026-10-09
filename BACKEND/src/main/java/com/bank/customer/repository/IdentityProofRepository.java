package com.bank.customer.repository;

import com.bank.customer.entity.IdentityProof;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface IdentityProofRepository extends JpaRepository<IdentityProof, Integer> {
    List<IdentityProof> findByCustomerId(Long customerId);
    Optional<IdentityProof> findByIdAndCustomerId(Integer id, Long customerId);
}