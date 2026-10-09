package com.bank.product.service.impl;

import com.bank.common.exception.ResourceNotFoundException;
import com.bank.product.dto.request.CreateLoanRepaymentScheduleRequest;
import com.bank.product.dto.request.UpdateLoanRepaymentScheduleRequest;
import com.bank.product.dto.response.LoanRepaymentScheduleResponse;
import com.bank.product.entity.LoanRepaymentSchedule;
import com.bank.product.repository.LoanRepaymentScheduleRepository;
import com.bank.product.service.LoanRepaymentScheduleService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class LoanRepaymentScheduleServiceImpl
        implements LoanRepaymentScheduleService {

    private final LoanRepaymentScheduleRepository repository;

    public LoanRepaymentScheduleServiceImpl(
            LoanRepaymentScheduleRepository repository) {
        this.repository = repository;
    }

    @Override
    public LoanRepaymentScheduleResponse createLoanRepaymentSchedule(
            CreateLoanRepaymentScheduleRequest request) {

        LoanRepaymentSchedule schedule =
                new LoanRepaymentSchedule();

        schedule.setLoanAccountNo(request.getLoanAccountNo());
        schedule.setProductId(request.getProductId());
        schedule.setNote(request.getNote());

        LoanRepaymentSchedule savedSchedule =
                repository.save(schedule);

        return toResponse(savedSchedule);
    }

    @Override
    @Transactional(readOnly = true)
    public LoanRepaymentScheduleResponse
    getLoanRepaymentScheduleById(Integer scheduleId) {

        LoanRepaymentSchedule schedule = repository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan repayment schedule not found with id: "
                                + scheduleId));

        return toResponse(schedule);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoanRepaymentScheduleResponse>
    getAllLoanRepaymentSchedules() {

        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoanRepaymentScheduleResponse>
    getLoanRepaymentSchedulesByLoanAccountNo(
            String loanAccountNo) {

        return repository.findByLoanAccountNo(loanAccountNo)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoanRepaymentScheduleResponse>
    getLoanRepaymentSchedulesByProductId(Integer productId) {

        return repository.findByProductId(productId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public LoanRepaymentScheduleResponse
    updateLoanRepaymentSchedule(
            Integer scheduleId,
            UpdateLoanRepaymentScheduleRequest request) {

        LoanRepaymentSchedule schedule = repository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan repayment schedule not found with id: "
                                + scheduleId));

        if (request.getLoanAccountNo() != null) {
            schedule.setLoanAccountNo(request.getLoanAccountNo());
        }

        if (request.getProductId() != null) {
            schedule.setProductId(request.getProductId());
        }

        if (request.getNote() != null) {
            schedule.setNote(request.getNote());
        }

        LoanRepaymentSchedule updatedSchedule =
                repository.save(schedule);

        return toResponse(updatedSchedule);
    }

    @Override
    public void deleteLoanRepaymentSchedule(Integer scheduleId) {

        LoanRepaymentSchedule schedule = repository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan repayment schedule not found with id: "
                                + scheduleId));

        repository.delete(schedule);
    }

    private LoanRepaymentScheduleResponse toResponse(
            LoanRepaymentSchedule schedule) {

        return new LoanRepaymentScheduleResponse(
                schedule.getScheduleId(),
                schedule.getLoanAccountNo(),
                schedule.getProductId(),
                schedule.getNote(),
                schedule.getCreatedDate(),
                schedule.getUpdatedDate());
    }
}