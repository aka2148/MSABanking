package com.bank.fd.repository;

import com.bank.fd.entity.FdTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FdTransactionRepository extends JpaRepository<FdTransaction, Long> {
    List<FdTransaction> findByAccountNumber(String accountNumber);
}
