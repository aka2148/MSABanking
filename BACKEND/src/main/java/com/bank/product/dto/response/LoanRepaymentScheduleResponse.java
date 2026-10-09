package com.bank.product.dto.response;

import java.time.LocalDateTime;

public class LoanRepaymentScheduleResponse {

    private Integer scheduleId;
    private String loanAccountNo;
    private Integer productId;
    private String note;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    public LoanRepaymentScheduleResponse() {
    }

    public LoanRepaymentScheduleResponse(
            Integer scheduleId,
            String loanAccountNo,
            Integer productId,
            String note,
            LocalDateTime createdDate,
            LocalDateTime updatedDate) {

        this.scheduleId = scheduleId;
        this.loanAccountNo = loanAccountNo;
        this.productId = productId;
        this.note = note;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

    public Integer getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(Integer scheduleId) {
        this.scheduleId = scheduleId;
    }

    public String getLoanAccountNo() {
        return loanAccountNo;
    }

    public void setLoanAccountNo(String loanAccountNo) {
        this.loanAccountNo = loanAccountNo;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(LocalDateTime updatedDate) {
        this.updatedDate = updatedDate;
    }
}