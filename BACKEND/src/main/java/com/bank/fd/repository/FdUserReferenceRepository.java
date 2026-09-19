package com.bank.fd.repository;

import com.bank.fd.entity.FdUserReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FdUserReferenceRepository extends JpaRepository<FdUserReference, Long> {
    Optional<FdUserReference> findByUserUuid(String userUuid);
}
