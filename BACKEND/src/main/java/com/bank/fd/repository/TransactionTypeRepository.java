package com.bank.fd.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransactionTypeRepository extends JpaRepository<TransactionTypeReference, Long> {
    @Query("select type.id from TransactionTypeReference type where upper(type.code) = upper(:code)")
    Optional<Long> findIdByCode(@Param("code") String code);
}

@Entity
@Table(name = "transaction_types", schema = "public")
class TransactionTypeReference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tt_txn_type_id")
    private Long id;

    @Column(name = "tt_txn_type_code", nullable = false, unique = true, length = 50)
    private String code;

    public Long getId() { return id; }
}
