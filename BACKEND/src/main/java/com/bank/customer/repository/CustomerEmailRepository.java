package com.bank.customer.repository;

import com.bank.customer.entity.CustomerEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CustomerEmailRepository extends JpaRepository<CustomerEmail, Long> {
    List<CustomerEmail> findByCustomerId(Long customerId);
    Optional<CustomerEmail> findByIdAndCustomerId(Integer id, Long customerId);
}