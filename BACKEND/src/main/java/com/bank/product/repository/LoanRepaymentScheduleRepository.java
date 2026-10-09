package com.bank.product.repository;

import com.bank.product.entity.LoanRepaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoanRepaymentScheduleRepository
        extends JpaRepository<LoanRepaymentSchedule, Integer> {

    List<LoanRepaymentSchedule> findByLoanAccountNo(
            String loanAccountNo);

    List<LoanRepaymentSchedule> findByProductId(
            Integer productId);
}