package com.bank.fd.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Embeddable
public class FdJournalLine {
    @Column(name="jl_account_code", nullable=false, length=50) private String accountCode;
    @Column(name="jl_debit", nullable=false, precision=18, scale=4) private BigDecimal debit;
    @Column(name="jl_credit", nullable=false, precision=18, scale=4) private BigDecimal credit;
    protected FdJournalLine() { }
    public FdJournalLine(String code, BigDecimal debit, BigDecimal credit) {
        this.accountCode=code; this.debit=debit; this.credit=credit;
    }
    public String getAccountCode() { return accountCode; }
    public BigDecimal getDebit() { return debit; }
    public BigDecimal getCredit() { return credit; }
}
