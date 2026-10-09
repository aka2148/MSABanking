package com.bank.customer.repository;

import com.bank.customer.entity.CustomerPhone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CustomerPhoneRepository extends JpaRepository<CustomerPhone, Long> {
    List<CustomerPhone> findByCustomerId(Long customerId);
    Optional<CustomerPhone> findByIdAndCustomerId(Long id, Long customerId);
    default Optional<CustomerPhone> findByIdAndCustomerId(Integer id, Long customerId) { return findByIdAndCustomerId(id.longValue(), customerId); }
}
