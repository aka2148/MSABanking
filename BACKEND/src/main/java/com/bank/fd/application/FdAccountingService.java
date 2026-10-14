package com.bank.fd.application;
import com.bank.common.exception.*;
import com.bank.fd.entity.*;
import com.bank.fd.repository.*;
import com.bank.fd.domain.FdTimePolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Joins the caller's locked FD transaction. Never creates separate financial commits. */
@Service
public class FdAccountingService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private final SavingsAccountRepository savings;
    private final FdJournalRepository journals;
    private final FdTimePolicy time;
    public FdAccountingService(SavingsAccountRepository savings,FdJournalRepository journals,FdTimePolicy time) {
        this.savings=savings;this.journals=journals;this.time=time;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void fund(FdAccount fd) {
        String key=key(fd,"FUNDING");
        if(journals.findByJournalKey(key).isPresent()) throw new InvalidInputException("FD funding already recorded; savings will not be debited again");
        SavingsAccount source=lockedSource(fd);
        BigDecimal principal=fd.getPrincipalAmount();
        if(principal == null || principal.signum() <= 0) throw new InvalidInputException("FD principal must be positive");
        source.setBalance(source.getBalance().subtract(principal)); savings.save(source);
        post(fd,source,"FUNDING",key,List.of(debit("SAVINGS_LIABILITY",principal),credit("FD_PRINCIPAL_LIABILITY",principal)));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void recognizeMaturity(FdAccount fd,BigDecimal interest) {
        // Historical FDs retain their existing maturity calculation, but cannot be paid without a verified source.
        if(fd.getFundingSavingsAccountId()==null) return;
        requireFunded(fd);
        String key=key(fd,"INTEREST");
        if(journals.findByJournalKey(key).isPresent()) return;
        if(interest==null || interest.signum()<0) throw new InvalidInputException("Maturity interest cannot be negative");
        if(interest.signum()==0) return;
        SavingsAccount source=lockedSource(fd);
        post(fd,source,"INTEREST",key,List.of(debit("INTEREST_EXPENSE",interest),credit("ACCRUED_INTEREST_PAYABLE",interest)));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public FdJournal settle(FdAccount fd,BigDecimal payout) {
        var existing=journals.findByJournalKey(key(fd,"SETTLEMENT"));
        if(existing.isPresent()) return existing.get();
        requireFunded(fd);
        BigDecimal principal=fd.getPrincipalAmount();
        if(payout==null || payout.compareTo(principal)<0) throw new InvalidInputException("Settlement cannot be below FD principal");
        BigDecimal interest=payout.subtract(principal);
        if(interest.signum()>0) {
            FdJournal accrued=journals.findByJournalKey(key(fd,"INTEREST"))
                .orElseThrow(() -> new InvalidInputException("Maturity interest has not been recognized"));
            if(accrued.getTotalCredit().compareTo(interest)!=0) throw new InvalidInputException("Interest payable does not match the settlement");
        }
        SavingsAccount source=lockedSource(fd);
        source.setBalance(source.getBalance().add(payout)); savings.save(source);
        List<FdJournalLine> lines=new ArrayList<>(); lines.add(debit("FD_PRINCIPAL_LIABILITY",principal));
        if(interest.signum()>0) lines.add(debit("ACCRUED_INTEREST_PAYABLE",interest));
        lines.add(credit("SAVINGS_LIABILITY",payout));
        return post(fd,source,"SETTLEMENT",key(fd,"SETTLEMENT"),lines);
    }
    @Transactional(readOnly=true)
    public Optional<FdJournal> settlement(Long fdId) { return journals.findByJournalKey("FD:"+fdId+":SETTLEMENT"); }
    /** Presentation-only opening cash, explicitly journaled; caller locks the owned savings account. */
    @Transactional(propagation=Propagation.MANDATORY)
    public FdJournal provisionDemoOpening(SavingsAccount source,BigDecimal amount) {
        String key="SAVINGS:"+source.getId()+":DEMO_OPENING";
        var existing=journals.findByJournalKey(key); if(existing.isPresent()) return existing.get();
        if(amount==null || amount.signum()<=0 || amount.scale()>4 || amount.precision()>18)
            throw new InvalidInputException("Enter a positive demo opening amount with at most four decimal places");
        source.setBalance(source.getBalance().add(amount)); savings.save(source);
        return post(null,source,"DEMO_OPENING",key,List.of(debit("DEMO_CASH",amount),credit("SAVINGS_LIABILITY",amount)));
    }
    private void requireFunded(FdAccount fd) {
        FdJournal funding=journals.findByJournalKey(key(fd,"FUNDING"))
            .orElseThrow(() -> new InvalidInputException("FD has no verified savings funding journal; historical funding must be reconciled first"));
        if(!Objects.equals(funding.getSavingsAccountId(),fd.getFundingSavingsAccountId())
                || funding.getTotalCredit().compareTo(fd.getPrincipalAmount())!=0)
            throw new InvalidInputException("FD funding journal does not match the source and principal");
    }
    private SavingsAccount lockedSource(FdAccount fd) {
        if(fd.getFundingSavingsAccountId()==null) throw new InvalidInputException("FD has no original funding savings account");
        SavingsAccount source=savings.findByIdForUpdate(fd.getFundingSavingsAccountId())
            .orElseThrow(() -> new ResourceNotFoundException("Original savings account not found"));
        if(!Objects.equals(source.getHolderId(),fd.getPrimaryHolderId()) || !Objects.equals(source.getCurrencyId(),fd.getCurrencyId()))
            throw new InvalidInputException("FD savings ownership or currency does not match");
        if(!"ACTIVE".equals(source.getStatus())) throw new InvalidInputException("Original savings account is inactive");
        return source;
    }
    private FdJournal post(FdAccount fd,SavingsAccount source,String operation,String key,List<FdJournalLine> lines) {
        BigDecimal debit=ZERO,credit=ZERO;
        for(var line:lines) {
            if(line.getDebit().signum()<0 || line.getCredit().signum()<0
                || (line.getDebit().signum()>0)==(line.getCredit().signum()>0))
                throw new InvalidInputException("Invalid journal line");
            debit=debit.add(line.getDebit());credit=credit.add(line.getCredit());
        }
        if(debit.signum()<=0 || debit.compareTo(credit)!=0) throw new InvalidInputException("Journal must have equal positive debit and credit totals");
        FdJournal journal=new FdJournal(); journal.setJournalKey(key); journal.setOperation(operation);
        journal.setFdAccountId(fd==null?null:fd.getId());journal.setSavingsAccountId(source.getId());journal.setCurrencyId(source.getCurrencyId());
        journal.setPostedAt(OffsetDateTime.ofInstant(time.now(),ZoneOffset.UTC));
        journal.setTotalDebit(debit);journal.setTotalCredit(credit);journal.setSavingsBalanceAfter(source.getBalance());journal.setLines(lines);
        return journals.saveAndFlush(journal);
    }
    private static String key(FdAccount fd,String operation){return "FD:"+fd.getId()+":"+operation;}
    private static FdJournalLine debit(String code,BigDecimal value){return new FdJournalLine(code,value,ZERO);}
    private static FdJournalLine credit(String code,BigDecimal value){return new FdJournalLine(code,ZERO,value);}
}
