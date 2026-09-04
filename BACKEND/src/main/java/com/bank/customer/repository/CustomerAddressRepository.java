package com.bank.customer.repository;

import com.bank.customer.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {
    List<CustomerAddress> findByCustomerId(Long customerId);
    Optional<CustomerAddress> findByIdAndCustomerId(Long id, Long customerId);
}