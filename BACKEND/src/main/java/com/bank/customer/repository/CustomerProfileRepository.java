package com.bank.customer.repository;

import com.bank.customer.entity.CustomerProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {

    Optional<CustomerProfile> findByUserId(Long userId);

    Optional<CustomerProfile> findByUserCredentialsUsername(String username);

    /**
     * Filtered, paginated customer search.
     * All parameters are optional — pass null to skip a filter.
     *
     * @param status kycStatus value (e.g. "ACTIVE", "PENDING")
     * @param risk   riskCategory value (e.g. "LOW", "HIGH")
     * @param search free-text matched against customerNumber OR customer full name (case-insensitive)
     */
    @Query("""
        SELECT c FROM CustomerProfile c
        LEFT JOIN c.details d
        WHERE (:status IS NULL OR c.kycStatus = :status)
          AND (:risk   IS NULL OR c.riskCategory = :risk)
          AND (:search IS NULL
               OR LOWER(c.customerNumber) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(d.fullName)       LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<CustomerProfile> findWithFilters(
            @Param("status") String status,
            @Param("risk")   String risk,
            @Param("search") String search,
            Pageable pageable);
}
