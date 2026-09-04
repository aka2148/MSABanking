package com.bank.customer.repository;

import com.bank.customer.entity.UserPasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserPasswordHistoryRepository extends JpaRepository<UserPasswordHistory, Long> {
    List<UserPasswordHistory> findByUserIdOrderByChangedAtDesc(Long userId);
}