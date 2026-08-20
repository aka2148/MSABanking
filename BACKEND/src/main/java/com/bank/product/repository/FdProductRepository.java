package com.bank.product.repository;

import com.bank.product.entity.FdProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FdProductRepository extends JpaRepository<FdProduct, Long> {
    List<FdProduct> findByIsActiveTrue();
}
