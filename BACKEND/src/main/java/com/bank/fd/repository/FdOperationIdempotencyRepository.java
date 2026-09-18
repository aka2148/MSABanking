package com.bank.fd.repository;

import com.bank.fd.entity.FdOperationIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FdOperationIdempotencyRepository extends JpaRepository<FdOperationIdempotency, Long> {
    Optional<FdOperationIdempotency> findByOperationScopeAndActorKeyAndIdempotencyKey(
            String operationScope,
            String actorKey,
            String idempotencyKey);
}
