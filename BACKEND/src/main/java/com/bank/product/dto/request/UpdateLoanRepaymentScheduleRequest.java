package com.bank.product.dto.request;

import jakarta.validation.constraints.Size;

public class UpdateLoanRepaymentScheduleRequest {

    @Size(max = 64)
    private String loanAccountNo;

    private Integer productId;

    @Size(max = 1024)
    private String note;

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
}