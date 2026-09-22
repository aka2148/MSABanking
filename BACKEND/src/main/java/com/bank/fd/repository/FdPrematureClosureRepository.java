package com.bank.fd.repository;

import com.bank.fd.entity.FdPrematureClosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FdPrematureClosureRepository extends JpaRepository<FdPrematureClosure, Long> {
    List<FdPrematureClosure> findByAccountIdOrderByClosureDateAscIdAsc(Long accountId);
    List<FdPrematureClosure> findByAccountIdAndStatus(Long accountId, String status);
}
