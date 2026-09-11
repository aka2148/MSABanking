package com.bank.customer.repository;

import com.bank.customer.entity.BankRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankRoleRepository extends JpaRepository<BankRole, Long> {
    Optional<BankRole> findByCode(String code);
}