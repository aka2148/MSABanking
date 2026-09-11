package com.bank.product.repository;

import com.bank.product.entity.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleTypeRepository extends JpaRepository<RoleType, Integer> {
    Optional<RoleType> findByRoleTypeCode(String roleTypeCode);
}
