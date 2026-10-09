package com.bank.product.service;

import com.bank.product.dto.request.CreateLoanRepaymentScheduleRequest;
import com.bank.product.dto.request.UpdateLoanRepaymentScheduleRequest;
import com.bank.product.dto.response.LoanRepaymentScheduleResponse;

import java.util.List;

public interface LoanRepaymentScheduleService {

    LoanRepaymentScheduleResponse createLoanRepaymentSchedule(
            CreateLoanRepaymentScheduleRequest request);

    LoanRepaymentScheduleResponse getLoanRepaymentScheduleById(
            Integer scheduleId);

    List<LoanRepaymentScheduleResponse>
    getAllLoanRepaymentSchedules();

    List<LoanRepaymentScheduleResponse>
    getLoanRepaymentSchedulesByLoanAccountNo(
            String loanAccountNo);

    List<LoanRepaymentScheduleResponse>
    getLoanRepaymentSchedulesByProductId(
            Integer productId);

    LoanRepaymentScheduleResponse updateLoanRepaymentSchedule(
            Integer scheduleId,
            UpdateLoanRepaymentScheduleRequest request);

    void deleteLoanRepaymentSchedule(Integer scheduleId);
}