package com.bank.customer.repository;
import com.bank.customer.entity.Branch; import org.springframework.data.jpa.repository.JpaRepository;
public interface BranchRepository extends JpaRepository<Branch,Long> { }