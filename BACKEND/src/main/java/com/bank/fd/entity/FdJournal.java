package com.bank.fd.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Journal is also the immutable settlement receipt when operation is SETTLEMENT. */
@Entity @Table(name="fd_journals", schema="public")
public class FdJournal {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="fj_id") private Long id;
    @Column(name="fj_key", nullable=false, unique=true, length=100) private String journalKey;
    @Column(name="fj_fd_account_id") private Long fdAccountId;
    @Column(name="fj_savings_account_id", nullable=false) private Long savingsAccountId;
    @Column(name="fj_currency_id", nullable=false) private Long currencyId;
    @Column(name="fj_operation", nullable=false, length=30) private String operation;
    @Column(name="fj_posted_at", nullable=false) private OffsetDateTime postedAt;
    @Column(name="fj_total_debit", nullable=false, precision=18, scale=4) private BigDecimal totalDebit;
    @Column(name="fj_total_credit", nullable=false, precision=18, scale=4) private BigDecimal totalCredit;
    @Column(name="fj_savings_balance_after", precision=18, scale=4) private BigDecimal savingsBalanceAfter;
    @ElementCollection
    @CollectionTable(name="fd_journal_lines", schema="public", joinColumns=@JoinColumn(name="jl_journal_id"))
    @OrderColumn(name="jl_sequence") private List<FdJournalLine> lines = new ArrayList<>();
    public Long getId(){return id;}
    public String getJournalKey(){return journalKey;} public void setJournalKey(String v){journalKey=v;}
    public Long getFdAccountId(){return fdAccountId;} public void setFdAccountId(Long v){fdAccountId=v;}
    public Long getSavingsAccountId(){return savingsAccountId;} public void setSavingsAccountId(Long v){savingsAccountId=v;}
    public Long getCurrencyId(){return currencyId;} public void setCurrencyId(Long v){currencyId=v;}
    public String getOperation(){return operation;} public void setOperation(String v){operation=v;}
    public OffsetDateTime getPostedAt(){return postedAt;} public void setPostedAt(OffsetDateTime v){postedAt=v;}
    public BigDecimal getTotalDebit(){return totalDebit;} public void setTotalDebit(BigDecimal v){totalDebit=v;}
    public BigDecimal getTotalCredit(){return totalCredit;} public void setTotalCredit(BigDecimal v){totalCredit=v;}
    public BigDecimal getSavingsBalanceAfter(){return savingsBalanceAfter;} public void setSavingsBalanceAfter(BigDecimal v){savingsBalanceAfter=v;}
    public List<FdJournalLine> getLines(){return lines;} public void setLines(List<FdJournalLine> v){lines=new ArrayList<>(v);}
}
