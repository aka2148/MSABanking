package com.bank.fd.repository;

import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FdAccountRepository extends JpaRepository<FdAccount, Long> {
    Optional<FdAccount> findByAccountNumber(String accountNumber);
    List<FdAccount> findByPrimaryHolderId(Long primaryHolderId);
    List<FdAccount> findByPrimaryHolderIdAndStatus(Long primaryHolderId, FdStatus status);
    List<FdAccount> findByStatus(FdStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM FdAccount a WHERE a.id = :id")
    Optional<FdAccount> findByIdForUpdate(@Param("id") Long id);

    default Optional<FdAccount> findByIdForUpdateAndHolder(Long id, Long holderId) {
        return findByIdForUpdate(id).filter(account -> holderId.equals(account.getPrimaryHolderId()));
    }

    @Query("SELECT MAX(a.accountNumber) FROM FdAccount a WHERE a.accountNumber LIKE :prefix%")
    String findMaxAccountNumberByPrefix(@Param("prefix") String prefix);
}
