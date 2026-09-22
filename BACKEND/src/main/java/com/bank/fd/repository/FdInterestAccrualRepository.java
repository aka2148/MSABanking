package com.bank.fd.repository;

import com.bank.fd.entity.FdInterestAccrual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FdInterestAccrualRepository extends JpaRepository<FdInterestAccrual, Long> {
    List<FdInterestAccrual> findByAccountIdOrderByAccrualDateAscIdAsc(Long accountId);
    Optional<FdInterestAccrual> findByAccountIdAndAccrualDate(Long accountId, OffsetDateTime accrualDate);
}
