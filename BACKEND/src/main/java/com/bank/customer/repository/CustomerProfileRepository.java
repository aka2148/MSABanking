package com.bank.customer.repository;

import com.bank.customer.entity.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {
    Optional<CustomerProfile> findByUserId(Long userId);
    Optional<CustomerProfile> findByUserUsername(String username);
}
