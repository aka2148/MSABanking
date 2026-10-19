package com.bank.fd.repository;

import com.bank.fd.entity.SavingsTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavingsTransactionRepository extends JpaRepository<SavingsTransaction, Long> {
    List<SavingsTransaction> findByReferenceIdOrderByIdAsc(String referenceId);
}
