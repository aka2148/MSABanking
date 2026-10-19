package com.bank.fd.repository;
import com.bank.fd.entity.SavingsAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface SavingsAccountRepository extends JpaRepository<SavingsAccount, Long> {
    List<SavingsAccount> findByHolderIdOrderByIdAsc(Long holderId);
    Optional<SavingsAccount> findByAccountNumber(String accountNumber);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SavingsAccount s WHERE s.id = :id")
    Optional<SavingsAccount> findByIdForUpdate(@Param("id") Long id);
}
