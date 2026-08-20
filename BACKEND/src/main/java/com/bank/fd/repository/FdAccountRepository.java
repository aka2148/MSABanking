package com.bank.fd.repository;

import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FdAccountRepository extends JpaRepository<FdAccount, Long> {
    Optional<FdAccount> findByAccountNumber(String accountNumber);
    List<FdAccount> findByCustomerId(Long customerId);
    List<FdAccount> findByStatus(FdStatus status);

    @Query("SELECT MAX(a.accountNumber) FROM FdAccount a WHERE a.accountNumber LIKE :prefix%")
    String findMaxAccountNumberByPrefix(@Param("prefix") String prefix);
}
