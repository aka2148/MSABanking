package com.bank.fdservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@RestController
public class FdLifecycleController {
    private static final BigDecimal DEFAULT_PREMATURE_PENALTY_POINTS = new BigDecimal("1.0000");
    private final JdbcTemplate jdbc; private final FdBatchService batches; private final ObjectMapper json; private final DemoClockService clock;private final MpinProofVerifier mpin;
    private final PostingService postings; private final FdLifecycleRecordService lifecycleRecords;
    public FdLifecycleController(JdbcTemplate jdbc,FdBatchService batches,ObjectMapper json,DemoClockService clock,MpinProofVerifier mpin,PostingService postings,FdLifecycleRecordService lifecycleRecords){this.jdbc=jdbc;this.batches=batches;this.json=json;this.clock=clock;this.mpin=mpin;this.postings=postings;this.lifecycleRecords=lifecycleRecords;}

    @PostMapping("/api/v1/admin/fixed-deposits/accrual-batch") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public FdBatchService.BatchResult accrue(@RequestParam(required=false) LocalDate businessDate){return batches.accrue(businessDate);}

    @PostMapping("/api/v1/admin/fixed-deposits/maturity-batch") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public FdBatchService.BatchResult mature(@RequestParam(required=false) LocalDate businessDate){return batches.mature(businessDate);}

    @PostMapping("/api/v1/fixed-deposits/{id}/close") @Transactional
    public FdCloseResponse close(Authentication actor,@PathVariable long id,@RequestHeader("Idempotency-Key") @NotBlank @Size(max=255) String key,@RequestHeader(value="X-MPIN-Proof",required=false)String proof){
        long user=userId(actor);String hash=hash(Long.toString(id));Long replay=claim("FD_CLOSE",user,key,hash);if(replay!=null)return closeResponse(replay,user);
        Map<String,Object> fd=fd(id);
        if(((Number)fd.get("owner")).longValue()!=user)throw notFound();
        String jti=mpin.verify(actor,proof,"FD_CLOSE",user,MpinProofVerifier.canonical("FD_CLOSE",id));mpin.consume(jti,user,"FD_CLOSE");
        if(!"MATURED".equals(fd.get("status")))throw conflict("Only a matured FD can be closed normally");
        verifyMaturityJournals(id,((Number)fd.get("savings")).longValue(),(BigDecimal)fd.get("principal"),(BigDecimal)fd.get("maturityAmount"));
        return settle(id,user,user,key,"FD_CLOSE",fd,(BigDecimal)fd.get("maturityAmount"),"CLOSED","MATURITY_SETTLEMENT");
    }

    @PostMapping("/api/v1/fixed-deposits/{id}/premature-closure") @Transactional
    public PrematureQuote requestPremature(Authentication actor,@PathVariable long id,@RequestHeader("Idempotency-Key") @NotBlank @Size(max=255) String key,@RequestHeader(value="X-MPIN-Proof",required=false)String proof,@Valid @RequestBody PrematureRequest request){
        long user=userId(actor);String reason=request.reason==null?"":request.reason.trim();
        Map<String,Object> fd=fd(id);if(((Number)fd.get("owner")).longValue()!=user)throw notFound();
        String hash=hash(id+"|"+reason);Long replay=claim("PREMATURE_REQUEST",user,key,hash);if(replay!=null)return quote(replay,user);
        String jti=mpin.verify(actor,proof,"FD_PREMATURE_REQUEST",user,MpinProofVerifier.canonical("FD_PREMATURE_REQUEST",id));mpin.consume(jti,user,"FD_PREMATURE_REQUEST");
        expireStaleQuotes(id);
        if(!"ACTIVE".equals(fd.get("status")))throw conflict("Only an active FD can request premature closure");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM fd_premature_closure WHERE fpc_account_id=? AND fpc_status='PENDING'",Integer.class,id)>0)throw conflict("A premature closure request is already pending");
        long currency=((Number)fd.get("currency")).longValue();
        FrozenTerms terms=frozenTerms((String)fd.get("termsSnapshot"),(BigDecimal)fd.get("rate"));
        if(terms.annualRate()==null||terms.penaltyPoints()==null)throw conflict("Premature closure is unavailable because this FD has no complete booked rate and penalty snapshot");
        BigDecimal principal=(BigDecimal)fd.get("principal");
        LocalDate start=localDateTime(fd.get("opened")).toLocalDate();
        LocalDate maturity=((java.sql.Date)fd.get("maturity")).toLocalDate();
        LocalDate today=clock.businessDate();
        if(!today.isBefore(maturity))throw conflict("This FD has reached its maturity date; use the maturity workflow");
        int scale=currencyScale(currency);
        PrematureClosureCalculator.Quote calculated=PrematureClosureCalculator.calculate(
                principal,terms.annualRate(),terms.penaltyPoints(),start,today,scale);
        jdbc.update("INSERT INTO fd_premature_closure(fpc_account_id,fpc_closure_date,fpc_penalty_pct_applied,fpc_penalty_amount,fpc_net_payable_amount,fpc_currency_id,fpc_reason,fpc_status) VALUES(?,?,?,?,?,?,?,'PENDING')",
                id,Timestamp.from(clock.status().effectiveTime()),calculated.penaltyPoints(),calculated.penaltyAmount(),calculated.netPayable(),currency,reason.isBlank()?null:reason.substring(0,Math.min(reason.length(),500)));
        long closure=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);complete("PREMATURE_REQUEST",user,key,closure);return quote(closure,user);
    }

    @PostMapping("/api/v1/admin/fixed-deposits/{id}/premature-closure/{closureId}/approve") @Transactional @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public FdCloseResponse approvePremature(Authentication actor,@PathVariable long id,@PathVariable long closureId,@RequestHeader("Idempotency-Key") @NotBlank @Size(max=255) String key){
        long approver=userId(actor);String hash=hash(id+"|"+closureId);Long replay=claim("PREMATURE_APPROVAL",approver,key,hash);if(replay!=null)return closeResponse(id,approver,false);
        Map<String,Object> fd=fd(id);if(!"ACTIVE".equals(fd.get("status")))throw conflict("Only an active FD can be prematurely closed");
        Map<String,Object> q=jdbc.query("SELECT fpc_penalty_amount,fpc_net_payable_amount,fpc_currency_id,fpc_status,fpc_closure_date FROM fd_premature_closure WHERE fpc_closure_id=? AND fpc_account_id=? FOR UPDATE",r->{if(!r.next())return null;Map<String,Object> row=new HashMap<>();row.put("penalty",r.getBigDecimal(1));row.put("net",r.getBigDecimal(2));row.put("currency",r.getLong(3));row.put("status",r.getString(4));row.put("date",r.getTimestamp(5));return row;},closureId,id);
        if(q==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Premature closure request not found");if(!"PENDING".equals(q.get("status")))throw conflict("Premature closure request is already processed");
        long owner=((Number)fd.get("owner")).longValue();if(owner==approver)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Approver must be a different staff user");
        if(isExpired((Timestamp)q.get("date")))throw conflict("Premature closure quote expired at the end of its business day; request a fresh quote");
        BigDecimal net=(BigDecimal)q.get("net"), principal=(BigDecimal)fd.get("principal");
        if(net==null||principal==null||net.compareTo(principal)<0)throw conflict("Premature closure quote has no valid settlement amount");
        BigDecimal paidInterest=net.subtract(principal).setScale(currencyScale(((Number)q.get("currency")).longValue()),RoundingMode.HALF_UP);
        jdbc.update("UPDATE fd_premature_closure SET fpc_status='APPROVED',fpc_approved_by=?,fpc_decision_by=?,fpc_decision_at=? WHERE fpc_closure_id=?",approver,approver,Timestamp.from(clock.status().effectiveTime()),closureId);
        recognizePrematureInterest(id,((Number)fd.get("savings")).longValue(),((Number)q.get("currency")).longValue(),paidInterest);
        FdCloseResponse response=settle(id,owner,approver,key,"PREMATURE_APPROVAL",fd,net,"PREMATURELY_CLOSED","PREMATURE_SETTLEMENT");
        jdbc.update("UPDATE fd_premature_closure SET fpc_approved_by=? WHERE fpc_closure_id=?",approver,closureId);complete("PREMATURE_APPROVAL",approver,key,id);return response;
    }

    @PostMapping("/api/v1/admin/fixed-deposits/{id}/premature-closure/{closureId}/reject") @Transactional @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public PrematureQuote rejectPremature(Authentication actor,@PathVariable long id,@PathVariable long closureId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max=255) String key,@Valid @RequestBody DecisionRequest request){
        long approver=userId(actor);String reason=request.reason()==null?"":request.reason().trim();
        String hash=hash(id+"|"+closureId+"|"+reason);Long replay=claim("PREMATURE_REJECTION",approver,key,hash);
        if(replay!=null)return quote(replay,accountOwner(replay));
        Map<String,Object> fd=fd(id);
        Map<String,Object> closure=jdbc.query("SELECT fpc_status FROM fd_premature_closure WHERE fpc_closure_id=? AND fpc_account_id=? FOR UPDATE",r->r.next()?Map.of("status",r.getString(1)):null,closureId,id);
        if(closure==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Premature closure request not found");
        if(!"PENDING".equals(closure.get("status")))throw conflict("Premature closure request is already processed");
        long owner=((Number)fd.get("owner")).longValue();if(owner==approver)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Decision maker must be different from the FD owner");
        Timestamp requestedAt=jdbc.queryForObject("SELECT fpc_closure_date FROM fd_premature_closure WHERE fpc_closure_id=?",Timestamp.class,closureId);
        if(isExpired(requestedAt))throw conflict("Premature closure quote expired at the end of its business day; request a fresh quote");
        jdbc.update("UPDATE fd_premature_closure SET fpc_status='REJECTED',fpc_decision_by=?,fpc_decision_at=?,fpc_decision_reason=? WHERE fpc_closure_id=?",
                approver,Timestamp.from(clock.status().effectiveTime()),reason.isBlank()?null:reason.substring(0,Math.min(reason.length(),500)),closureId);
        event(id,"PREMATURE_CLOSURE_REJECTED",Map.of("closureId",closureId,"status","REJECTED","decisionBy",approver));
        complete("PREMATURE_REJECTION",approver,key,closureId);
        return quote(closureId,owner);
    }

    @GetMapping("/api/v1/admin/fixed-deposits/premature-closures") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public StaffQuotePage pendingPrematureClosures(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="25") int pageSize){
        if(page<0||page>1_000_000||pageSize<1||pageSize>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Page must be between 0 and 1000000 and pageSize must be between 1 and 100");
        expireStaleQuotes(null);
        Integer total=jdbc.queryForObject("SELECT COUNT(*) FROM fd_premature_closure q JOIN fd_accounts a ON a.fa_account_id=q.fpc_account_id WHERE q.fpc_status='PENDING' AND a.fa_status='ACTIVE'",Integer.class);
        List<StaffPrematureQuote> rows=jdbc.query("SELECT q.fpc_closure_id,q.fpc_account_id,a.fa_account_number,a.fa_principal_amount,a.fa_primary_holder_id FROM fd_premature_closure q JOIN fd_accounts a ON a.fa_account_id=q.fpc_account_id WHERE q.fpc_status='PENDING' AND a.fa_status='ACTIVE' ORDER BY q.fpc_closure_date,q.fpc_closure_id LIMIT ? OFFSET ?",
                (r,n)->new StaffPrematureQuote(r.getLong(2),r.getString(3),r.getBigDecimal(4),quote(r.getLong(1),r.getLong(5))),pageSize,Math.multiplyExact(page,pageSize));
        int count=total==null?0:total;
        return new StaffQuotePage(rows,page,pageSize,count);
    }

    @GetMapping("/api/v1/fixed-deposits/{id}/transactions")
    public List<Map<String,Object>> transactions(Authentication actor,@PathVariable long id){
        Map<String,Object> fd=fd(id);
        if(((Number)fd.get("owner")).longValue()!=userId(actor))throw notFound();
        String sql="SELECT record_type,record_id,DATE_FORMAT(occurred_at,'%Y-%m-%dT%H:%i:%s.%fZ') occurred_at,event_type,amount,balance_after,remarks,currency_code,source_savings_account_id FROM ("
                +"SELECT 'TRANSACTION' record_type,t.ft_transaction_id record_id,CASE WHEN q.fpc_closure_id IS NOT NULL AND tt.tt_txn_type_code IN ('FD_INTEREST','FD_PENALTY','FD_CLOSURE') THEN COALESCE(q.fpc_decision_at,t.ft_txn_date) ELSE t.ft_txn_date END occurred_at,tt.tt_txn_type_code event_type,CASE WHEN q.fpc_closure_id IS NOT NULL AND tt.tt_txn_type_code='FD_INTEREST' AND t.ft_remarks='Premature closure interest paid under booked terms' THEN t.ft_amount+COALESCE(q.fpc_penalty_amount,0) ELSE t.ft_amount END amount,t.ft_balance_after balance_after,t.ft_remarks remarks,c.fcr_iso_code currency_code,t.ft_source_savings_account_id source_savings_account_id FROM fd_transactions t JOIN transaction_types tt ON tt.tt_txn_type_id=t.ft_txn_type_id JOIN fd_currency_ref c ON c.fcr_currency_id=t.ft_currency_id LEFT JOIN fd_premature_closure q ON q.fpc_account_id=t.ft_account_id AND q.fpc_status='APPROVED' WHERE t.ft_account_id=? "
                +"UNION ALL SELECT 'ACCRUAL',a.fia_accrual_id,a.fia_accrual_date,'INTEREST_ACCRUAL',a.fia_accrued_amount,a.fia_cumulative_accrued,'Daily interest accrual',c.fcr_iso_code,NULL FROM fd_interest_accrual a JOIN fd_currency_ref c ON c.fcr_currency_id=a.fia_currency_id WHERE a.fia_account_id=? "
                +"UNION ALL SELECT 'TRANSACTION',-q.fpc_closure_id*2,COALESCE(q.fpc_decision_at,q.fpc_closure_date),'FD_INTEREST',q.fpc_net_payable_amount-a.fa_principal_amount+COALESCE(q.fpc_penalty_amount,0),NULL,'Full interest accrued before premature-closure penalty',c.fcr_iso_code,a.fa_funding_savings_account_id FROM fd_premature_closure q JOIN fd_accounts a ON a.fa_account_id=q.fpc_account_id JOIN fd_currency_ref c ON c.fcr_currency_id=q.fpc_currency_id WHERE q.fpc_account_id=? AND q.fpc_status='APPROVED' AND NOT EXISTS (SELECT 1 FROM fd_transactions t JOIN transaction_types tt ON tt.tt_txn_type_id=t.ft_txn_type_id WHERE t.ft_account_id=q.fpc_account_id AND tt.tt_txn_type_code='FD_INTEREST') "
                +"UNION ALL SELECT 'TRANSACTION',-q.fpc_closure_id*2+1,COALESCE(q.fpc_decision_at,q.fpc_closure_date),'FD_PENALTY',q.fpc_penalty_amount,NULL,'Premature-closure penalty deducted from accrued interest',c.fcr_iso_code,a.fa_funding_savings_account_id FROM fd_premature_closure q JOIN fd_accounts a ON a.fa_account_id=q.fpc_account_id JOIN fd_currency_ref c ON c.fcr_currency_id=q.fpc_currency_id WHERE q.fpc_account_id=? AND q.fpc_status='APPROVED' AND NOT EXISTS (SELECT 1 FROM fd_transactions t JOIN transaction_types tt ON tt.tt_txn_type_id=t.ft_txn_type_id WHERE t.ft_account_id=q.fpc_account_id AND tt.tt_txn_type_code='FD_PENALTY')"
                +") history ORDER BY occurred_at DESC,CASE event_type WHEN 'FD_CLOSURE' THEN 0 WHEN 'FD_SETTLEMENT' THEN 0 WHEN 'FD_PENALTY' THEN 1 WHEN 'FD_INTEREST' THEN 2 WHEN 'INTEREST_ACCRUAL' THEN 2 WHEN 'FD_MATURITY' THEN 2 WHEN 'FD_FUNDING' THEN 3 ELSE 2 END,record_id DESC";
        return jdbc.queryForList(sql,id,id,id,id);
    }

    @GetMapping("/api/v1/fixed-deposits/{id}/journals") @PreAuthorize("hasRole('ADMIN')")
    public List<JournalView> journals(@PathVariable long id){
        fd(id);
        return jdbc.query("SELECT j.fj_id,j.fj_operation,j.fj_posted_at,j.fj_savings_account_id,c.fcr_iso_code,j.fj_total_debit,j.fj_total_credit,j.fj_savings_balance_after FROM fd_journals j JOIN fd_currency_ref c ON c.fcr_currency_id=j.fj_currency_id WHERE j.fj_fd_account_id=? ORDER BY j.fj_id",
                (r,n)->new JournalView(r.getLong(1),r.getString(2),r.getTimestamp(3).toInstant().toString(),r.getLong(4),r.getString(5),r.getBigDecimal(6),r.getBigDecimal(7),r.getBigDecimal(8),jdbc.query("SELECT jl_account_code,jl_debit,jl_credit FROM fd_journal_lines WHERE jl_journal_id=? ORDER BY jl_sequence",(line,lineNumber)->new JournalLine(line.getString(1),line.getBigDecimal(2),line.getBigDecimal(3)),r.getLong(1))),id);
    }

    private void verifyMaturityJournals(long id,long savings,BigDecimal principal,BigDecimal maturityAmount){
        BigDecimal funding=journalCredits(id,savings,"FUNDING");
        if(funding.compareTo(principal)!=0)throw conflict("Verified funding records are required before this FD can be closed");
        BigDecimal expectedInterest=maturityAmount.subtract(principal).max(BigDecimal.ZERO);
        BigDecimal interest=journalCredits(id,savings,"INTEREST");
        if(interest.compareTo(expectedInterest)!=0)throw conflict("Verified maturity interest records are required before this FD can be closed");
    }
    private BigDecimal journalCredits(long id,long savings,String operation){
        BigDecimal amount=jdbc.queryForObject("SELECT COALESCE(SUM(fj_total_credit),0) FROM fd_journals WHERE fj_fd_account_id=? AND fj_savings_account_id=? AND fj_operation=?",BigDecimal.class,id,savings,operation);
        return amount==null?BigDecimal.ZERO:amount;
    }

    @GetMapping("/api/v1/fixed-deposits/{id}/accruals")
    public List<AccrualView> accruals(Authentication actor,@PathVariable long id){
        Map<String,Object> fd=fd(id);if(((Number)fd.get("owner")).longValue()!=userId(actor))throw notFound();
        return jdbc.query("SELECT a.fia_accrual_date,a.fia_accrued_amount,a.fia_cumulative_accrued,c.fcr_iso_code FROM fd_interest_accrual a JOIN fd_currency_ref c ON c.fcr_currency_id=a.fia_currency_id WHERE a.fia_account_id=? ORDER BY a.fia_accrual_date,a.fia_accrual_id",
                (r,n)->new AccrualView(id,r.getObject(1,java.time.LocalDateTime.class).toLocalDate().toString(),r.getBigDecimal(2),r.getBigDecimal(3),r.getString(4),(BigDecimal)fd.get("maturityAmount"),(String)fd.get("status")),id);
    }

    @GetMapping("/api/v1/fixed-deposits/{id}/accruals/cumulative")
    public AccrualView cumulativeAccrual(Authentication actor,@PathVariable long id){
        Map<String,Object> fd=fd(id);if(((Number)fd.get("owner")).longValue()!=userId(actor))throw notFound();
        return jdbc.queryForObject("SELECT COALESCE(MAX(i.fia_accrual_date),a.fa_open_date),COALESCE(MAX(i.fia_cumulative_accrued),0),c.fcr_iso_code FROM fd_accounts a JOIN fd_currency_ref c ON c.fcr_currency_id=a.fa_currency_id LEFT JOIN fd_interest_accrual i ON i.fia_account_id=a.fa_account_id WHERE a.fa_account_id=? GROUP BY a.fa_open_date,c.fcr_iso_code",
                (r,n)->new AccrualView(id,r.getObject(1,java.time.LocalDateTime.class).toLocalDate().toString(),BigDecimal.ZERO,r.getBigDecimal(2),r.getString(3),(BigDecimal)fd.get("maturityAmount"),(String)fd.get("status")),id);
    }

    @GetMapping("/api/v1/fixed-deposits/{id}/premature-closure")
    public List<PrematureQuote> prematureClosures(Authentication actor,@PathVariable long id){
        Map<String,Object> fd=fd(id);if(((Number)fd.get("owner")).longValue()!=userId(actor))throw notFound();
        expireStaleQuotes(id);
        List<Long> closureIds=jdbc.queryForList("SELECT fpc_closure_id FROM fd_premature_closure WHERE fpc_account_id=? ORDER BY fpc_closure_id DESC",Long.class,id);
        return closureIds.stream().map(closureId->quote(closureId,((Number)fd.get("owner")).longValue())).toList();
    }

    private FdCloseResponse settle(long id,long user,long idempotencyActor,String key,String scope,Map<String,Object> fd,BigDecimal amount,String nextStatus,String operation){
        if(amount==null)throw conflict("FD has no settlement amount");long savings=((Number)fd.get("savings")).longValue(),currency=((Number)fd.get("currency")).longValue();
        Integer eligible=jdbc.queryForObject("SELECT COUNT(*) FROM savings_accounts WHERE sa_account_id=? AND sa_holder_id=? AND sa_currency_id=? AND sa_status IN ('ACTIVE','INACTIVE')",Integer.class,savings,user,currency);
        if(eligible==null||eligible!=1)throw conflict("Settlement savings account is unavailable");
        int scale=currencyScale(currency);amount=amount.setScale(scale,RoundingMode.HALF_UP);
        Timestamp businessTimestamp=Timestamp.from(clock.status().effectiveTime());
        PostingService.MovementResult movement=postings.postSystemCredit(id,savings,currency,amount,"FD-SETTLEMENT:"+id,
                "FD settlement #"+id,Long.toString(idempotencyActor),"SETTLEMENT:"+id,
                "SETTLEMENT","FIXED_DEPOSIT","SAVINGS");
        BigDecimal after=movement.balanceAfter();
        if("PREMATURELY_CLOSED".equals(nextStatus)){
            BigDecimal penalty=jdbc.queryForObject("SELECT fpc_penalty_amount FROM fd_premature_closure WHERE fpc_account_id=? AND fpc_status='APPROVED' ORDER BY fpc_closure_id DESC LIMIT 1",BigDecimal.class,id);
            BigDecimal paidInterest=amount.subtract((BigDecimal)fd.get("principal")).setScale(scale,RoundingMode.HALF_UP);
            BigDecimal fullInterest=paidInterest.add(penalty==null?BigDecimal.ZERO:penalty).setScale(scale,RoundingMode.HALF_UP);
            if(fullInterest.signum()>0)jdbc.update("INSERT INTO fd_transactions(ft_account_id,ft_txn_type_id,ft_amount,ft_currency_id,ft_remarks,ft_created_by,ft_source_savings_account_id,ft_txn_date) VALUES(?,?,?,?,?,?,?,?)",id,typeId("FD_INTEREST"),fullInterest,currency,"Full interest accrued before premature-closure penalty",Long.toString(idempotencyActor),savings,businessTimestamp);
            if(penalty!=null&&penalty.signum()>0)jdbc.update("INSERT INTO fd_transactions(ft_account_id,ft_txn_type_id,ft_amount,ft_currency_id,ft_remarks,ft_created_by,ft_source_savings_account_id,ft_txn_date) VALUES(?,?,?,?,?,?,?,?)",id,typeId("FD_PENALTY"),penalty,currency,"Premature-closure penalty deducted from accrued interest",Long.toString(idempotencyActor),savings,businessTimestamp);
        }
        jdbc.update("INSERT INTO fd_transactions(ft_account_id,ft_txn_type_id,ft_amount,ft_balance_after,ft_currency_id,ft_remarks,ft_created_by,ft_source_savings_account_id,ft_txn_date) VALUES(?,?,?,?,?,?,?,?,?)",id,typeId("FD_CLOSURE"),amount,amount,currency,"Internal FD settlement; no external payment rail was called",Long.toString(idempotencyActor),savings,businessTimestamp);
        jdbc.update("UPDATE fd_accounts SET fa_status=?,fa_updated_by=?,fa_updated_date=UTC_TIMESTAMP(6),fa_row_version=fa_row_version+1 WHERE fa_account_id=?",nextStatus,Long.toString(idempotencyActor),id);
        event(id,nextStatus,Map.of("status",nextStatus,"settlementAmount",amount,"settledAt",clock.businessDate().toString()));
        lifecycleRecords.recordNoticeIfMissing(id,"CLOSED".equals(nextStatus)?"FD_CLOSED":"FD_PREMATURELY_CLOSED",clock.businessDate(),"fd-lifecycle");
        complete(scope,idempotencyActor,key,id);return new FdCloseResponse(id,nextStatus,amount,after);
    }

    private Map<String,Object> fd(long id){
        Map<String,Object> row=jdbc.query("SELECT fa_primary_holder_id,fa_product_id,fa_principal_amount,fa_currency_id,fa_status,fa_funding_savings_account_id,fa_maturity_amount,fa_interest_rate,fa_open_date,fa_maturity_date,fa_terms_snapshot FROM fd_accounts WHERE fa_account_id=? FOR UPDATE",r->{
            if(!r.next())return null;
            Map<String,Object> m=new HashMap<>();
            m.put("owner",r.getLong(1));
            m.put("product",r.getLong(2));
            m.put("principal",r.getBigDecimal(3));
            m.put("currency",r.getLong(4));
            m.put("status",r.getString(5));
            m.put("savings",r.getLong(6));
            m.put("maturityAmount",r.getBigDecimal(7)==null?BigDecimal.ZERO:r.getBigDecimal(7));
            m.put("rate",r.getBigDecimal(8));
            m.put("opened",r.getObject(9,java.time.LocalDateTime.class));
            m.put("maturity",r.getDate(10));
            m.put("termsSnapshot",r.getString(11)==null?"":r.getString(11));
            return m;
        },id);
        if(row==null)throw notFound();
        return row;
    }
    private PrematureQuote quote(long closure,long user){return jdbc.query("SELECT c.fpc_account_id,c.fpc_penalty_pct_applied,c.fpc_penalty_amount,c.fpc_net_payable_amount,c.fpc_status,c.fpc_closure_date,c.fpc_approved_by,c.fpc_decision_by,c.fpc_decision_reason,a.fa_principal_amount,a.fa_terms_snapshot,c.fpc_reason,cr.fcr_iso_code,a.fa_interest_rate FROM fd_premature_closure c JOIN fd_accounts a ON a.fa_account_id=c.fpc_account_id JOIN fd_currency_ref cr ON cr.fcr_currency_id=c.fpc_currency_id WHERE c.fpc_closure_id=? AND a.fa_primary_holder_id=?",r->{if(!r.next())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Premature closure request not found");BigDecimal points=r.getBigDecimal(2),penalty=r.getBigDecimal(3),net=r.getBigDecimal(4),principal=r.getBigDecimal(10);FrozenTerms terms=frozenTerms(r.getString(11),r.getBigDecimal(14));BigDecimal paid=net==null||principal==null?null:net.subtract(principal);BigDecimal full=paid==null||penalty==null?null:paid.add(penalty);BigDecimal penalized=terms.annualRate()==null||points==null?null:terms.annualRate().subtract(points.divide(new BigDecimal("100"),12,RoundingMode.HALF_UP)).max(BigDecimal.ZERO);return new PrematureQuote(closure,r.getLong(1),points,terms.annualRate(),penalized,paid,full,penalty,net,r.getString(5),r.getTimestamp(6).toInstant().toString(),r.getObject(7)==null?null:((Number)r.getObject(7)).longValue(),r.getObject(8)==null?null:((Number)r.getObject(8)).longValue(),r.getString(9),r.getString(12),r.getString(13));},closure,user);}
    private FdCloseResponse closeResponse(long id,long user){return closeResponse(id,user,true);}
    private FdCloseResponse closeResponse(long id,long user,boolean enforceOwner){String sql="SELECT a.fa_status,CASE WHEN a.fa_status='PREMATURELY_CLOSED' THEN (SELECT q.fpc_net_payable_amount FROM fd_premature_closure q WHERE q.fpc_account_id=a.fa_account_id AND q.fpc_status='APPROVED' ORDER BY q.fpc_closure_id DESC LIMIT 1) ELSE a.fa_maturity_amount END,s.sa_balance FROM fd_accounts a JOIN savings_accounts s ON s.sa_account_id=a.fa_funding_savings_account_id WHERE a.fa_account_id=?"+(enforceOwner?" AND a.fa_primary_holder_id=?":"");FdCloseResponse response=enforceOwner?jdbc.query(sql,rs->rs.next()?new FdCloseResponse(id,rs.getString(1),rs.getBigDecimal(2),rs.getBigDecimal(3)):null,id,user):jdbc.query(sql,rs->rs.next()?new FdCloseResponse(id,rs.getString(1),rs.getBigDecimal(2),rs.getBigDecimal(3)):null,id);if(response==null)throw notFound();return response;}
    private Long claim(String scope,long actor,String key,String hash){try{jdbc.update("INSERT INTO fd_operation_idempotency(foi_operation_scope,foi_actor_key,foi_idempotency_key,foi_request_hash,foi_status) VALUES(?,?,?,?,'PENDING')",scope,Long.toString(actor),key,hash);return null;}catch(DuplicateKeyException e){Map<String,Object> r=jdbc.queryForMap("SELECT foi_request_hash,foi_status,foi_aggregate_id FROM fd_operation_idempotency WHERE foi_operation_scope=? AND foi_actor_key=? AND foi_idempotency_key=?",scope,Long.toString(actor),key);if(!hash.equals(r.get("foi_request_hash")))throw conflict("Idempotency key was used for a different operation");if("COMPLETED".equals(r.get("foi_status"))&&r.get("foi_aggregate_id")!=null)return ((Number)r.get("foi_aggregate_id")).longValue();throw conflict("Operation is already in progress");}}
    private void complete(String scope,long actor,String key,long aggregate){jdbc.update("UPDATE fd_operation_idempotency SET foi_status='COMPLETED',foi_aggregate_id=?,foi_response_reference=?,foi_completed_at=UTC_TIMESTAMP(6) WHERE foi_operation_scope=? AND foi_actor_key=? AND foi_idempotency_key=?",aggregate,Long.toString(aggregate),scope,Long.toString(actor),key);}
    private long userId(Authentication a){Object x=a.getDetails() instanceof Map<?,?> d?d.get("userId"):null;if(!(x instanceof Number n))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Token has no user identity");return n.longValue();}
    private int currencyScale(long id){Integer n=jdbc.queryForObject("SELECT fcr_decimal_places FROM fd_currency_ref WHERE fcr_currency_id=?",Integer.class,id);return n==null?2:n;}
    private long accountOwner(long closureId){Long owner=jdbc.queryForObject("SELECT a.fa_primary_holder_id FROM fd_premature_closure q JOIN fd_accounts a ON a.fa_account_id=q.fpc_account_id WHERE q.fpc_closure_id=?",Long.class,closureId);if(owner==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Premature closure request not found");return owner;}
    private FrozenTerms frozenTerms(String snapshot,BigDecimal bookedRate){try{com.fasterxml.jackson.databind.JsonNode root=snapshot==null||snapshot.isBlank()?json.createObjectNode():json.readTree(snapshot);BigDecimal annualRate=decimal(root.path("annualRate"));BigDecimal penaltyPoints=decimal(root.path("prematurePenaltyPoints"));return new FrozenTerms(annualRate==null?bookedRate:annualRate,penaltyPoints==null?DEFAULT_PREMATURE_PENALTY_POINTS:penaltyPoints);}catch(Exception e){return new FrozenTerms(bookedRate,DEFAULT_PREMATURE_PENALTY_POINTS);}}
    private BigDecimal decimal(com.fasterxml.jackson.databind.JsonNode node){if(node==null||node.isMissingNode()||node.isNull()||node.asText().isBlank())return null;try{return new BigDecimal(node.asText());}catch(NumberFormatException e){return null;}}
    private boolean isExpired(Timestamp created){if(created==null)return true;ZoneId zone=ZoneId.of(clock.status().businessZone());LocalDate quoteDate=created.toInstant().atZone(zone).toLocalDate();return quoteDate.isBefore(clock.businessDate());}
    private void expireStaleQuotes(Long accountId){String sql="SELECT fpc_closure_id,fpc_closure_date FROM fd_premature_closure WHERE fpc_status='PENDING'"+(accountId==null?"":" AND fpc_account_id=?");List<StaleQuote> pending=accountId==null?jdbc.query(sql,(r,n)->new StaleQuote(r.getLong(1),r.getTimestamp(2))):jdbc.query(sql,(r,n)->new StaleQuote(r.getLong(1),r.getTimestamp(2)),accountId);for(StaleQuote quote:pending)if(isExpired(quote.createdAt()))jdbc.update("UPDATE fd_premature_closure SET fpc_status='EXPIRED',fpc_decision_at=? WHERE fpc_closure_id=? AND fpc_status='PENDING'",Timestamp.from(clock.status().effectiveTime()),quote.id());}
    private void recognizePrematureInterest(long fdId,long savingsId,long currencyId,BigDecimal interest){if(interest.signum()==0)return;String key="INTEREST:"+fdId+":PREMATURE";if(existsJournal(key)){BigDecimal recorded=jdbc.queryForObject("SELECT fj_total_credit FROM fd_journals WHERE fj_key=?",BigDecimal.class,key);if(recorded.compareTo(interest)!=0)throw conflict("Premature interest journal does not match the approved quote");return;}jdbc.update("INSERT INTO fd_journals(fj_key,fj_fd_account_id,fj_savings_account_id,fj_currency_id,fj_operation,fj_posted_at,fj_total_debit,fj_total_credit,fj_savings_balance_after) VALUES(?,?,?,?,'INTEREST',UTC_TIMESTAMP(6),?,?,NULL)",key,fdId,savingsId,currencyId,interest,interest);long journal=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);jdbc.update("INSERT INTO fd_journal_lines(jl_journal_id,jl_sequence,jl_account_code,jl_debit,jl_credit) VALUES(?,1,'INTEREST_EXPENSE',?,0),(?,2,'FIXED_DEPOSIT',0,?)",journal,interest,journal,interest);}
    private long typeId(String code){return jdbc.queryForObject("SELECT tt_txn_type_id FROM transaction_types WHERE tt_txn_type_code=?",Long.class,code);}
    private java.time.LocalDateTime localDateTime(Object value){if(value instanceof java.time.LocalDateTime dateTime)return dateTime;if(value instanceof Timestamp timestamp)return timestamp.toLocalDateTime();throw new IllegalStateException("Unexpected MySQL DATETIME value: "+(value==null?"null":value.getClass().getName()));}
    private boolean existsJournal(String key){return Boolean.TRUE.equals(jdbc.query("SELECT fj_id FROM fd_journals WHERE fj_key=?",(org.springframework.jdbc.core.ResultSetExtractor<Boolean>)r->r.next(),key));}
    private void event(long id,String type,Object payload){try{jdbc.update("INSERT INTO fd_account_events(fae_account_id,fae_event_type,fae_payload,fae_aggregate_version,fae_created_by) VALUES(?,?,CAST(? AS JSON),COALESCE((SELECT fa_row_version FROM fd_accounts WHERE fa_account_id=?),0),?)",id,type,json.writeValueAsString(payload),id,"fd-workflow");}catch(Exception e){throw new IllegalStateException(e);}}
    private String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Fixed deposit not found");}
    private ResponseStatusException conflict(String s){return new ResponseStatusException(HttpStatus.CONFLICT,s);}

    public record PrematureRequest(@Size(max=500) String reason){}
    public record DecisionRequest(@Size(max=500) String reason){}
    private record FrozenTerms(BigDecimal annualRate,BigDecimal penaltyPoints){}
    private record StaleQuote(long id,Timestamp createdAt){}
    public record PrematureQuote(@com.fasterxml.jackson.annotation.JsonProperty("closure_id") long closureId,@com.fasterxml.jackson.annotation.JsonProperty("account_id") long accountId,@com.fasterxml.jackson.annotation.JsonProperty("penalty_points") BigDecimal penaltyPoints,@com.fasterxml.jackson.annotation.JsonProperty("annual_rate") BigDecimal annualRate,@com.fasterxml.jackson.annotation.JsonProperty("penalized_rate") BigDecimal penalizedRate,@com.fasterxml.jackson.annotation.JsonProperty("paid_interest") BigDecimal paidInterest,@com.fasterxml.jackson.annotation.JsonProperty("full_rate_interest") BigDecimal fullRateInterest,@com.fasterxml.jackson.annotation.JsonProperty("penalty_amount") BigDecimal penaltyAmount,@com.fasterxml.jackson.annotation.JsonProperty("net_payable_amount") BigDecimal netPayableAmount,String status,@com.fasterxml.jackson.annotation.JsonProperty("requested_date") String requestedDate,@com.fasterxml.jackson.annotation.JsonProperty("approved_by") Long approvedBy,@com.fasterxml.jackson.annotation.JsonProperty("decision_by") Long decisionBy,@com.fasterxml.jackson.annotation.JsonProperty("decision_reason") String decisionReason,@com.fasterxml.jackson.annotation.JsonProperty("request_reason") String requestReason,@com.fasterxml.jackson.annotation.JsonProperty("currency_code") String currencyCode){}
    public record StaffPrematureQuote(@com.fasterxml.jackson.annotation.JsonProperty("account_id") long accountId,@com.fasterxml.jackson.annotation.JsonProperty("account_number") String accountNumber,BigDecimal principal,PrematureQuote quote){}
    public record StaffQuotePage(List<StaffPrematureQuote> content,int page,int pageSize,int totalElements){}
    public record JournalView(@com.fasterxml.jackson.annotation.JsonProperty("journal_id") long journalId,String operation,@com.fasterxml.jackson.annotation.JsonProperty("posted_at") String postedAt,@com.fasterxml.jackson.annotation.JsonProperty("savings_account_id") long savingsAccountId,@com.fasterxml.jackson.annotation.JsonProperty("currency_code") String currencyCode,@com.fasterxml.jackson.annotation.JsonProperty("total_debit") BigDecimal totalDebit,@com.fasterxml.jackson.annotation.JsonProperty("total_credit") BigDecimal totalCredit,@com.fasterxml.jackson.annotation.JsonProperty("savings_balance_after") BigDecimal savingsBalanceAfter,List<JournalLine> lines){}
    public record JournalLine(@com.fasterxml.jackson.annotation.JsonProperty("account_code") String accountCode,BigDecimal debit,BigDecimal credit){}
    public record AccrualView(@com.fasterxml.jackson.annotation.JsonProperty("account_id") long accountId,@com.fasterxml.jackson.annotation.JsonProperty("accrual_date") String accrualDate,@com.fasterxml.jackson.annotation.JsonProperty("accrued_amount") BigDecimal accruedAmount,@com.fasterxml.jackson.annotation.JsonProperty("cumulative_accrued") BigDecimal cumulativeAccrued,@com.fasterxml.jackson.annotation.JsonProperty("currency_code") String currencyCode,@com.fasterxml.jackson.annotation.JsonProperty("maturity_amount") BigDecimal maturityAmount,String status){}
    public record FdCloseResponse(long accountId,String status,BigDecimal settledAmount,BigDecimal savingsBalanceAfter){}
}
