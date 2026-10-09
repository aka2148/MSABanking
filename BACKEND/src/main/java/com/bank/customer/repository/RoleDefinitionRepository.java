package com.bank.customer.repository;

import com.bank.customer.entity.RoleDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleDefinitionRepository extends JpaRepository<RoleDefinition, Long> {
    Optional<RoleDefinition> findByCode(String code);
    default Optional<RoleDefinition> findById(Integer id) { return findById(id.longValue()); }
}
