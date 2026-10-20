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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class FdLifecycleController {
    private final JdbcTemplate jdbc; private final FdBatchService batches; private final ObjectMapper json; private final DemoClockService clock;
    public FdLifecycleController(JdbcTemplate jdbc,FdBatchService batches,ObjectMapper json,DemoClockService clock){this.jdbc=jdbc;this.batches=batches;this.json=json;this.clock=clock;}

    @PostMapping("/api/v1/admin/fixed-deposits/accrual-batch") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public FdBatchService.BatchResult accrue(@RequestParam(required=false) LocalDate businessDate){return batches.accrue(businessDate);}

    @PostMapping("/api/v1/admin/fixed-deposits/maturity-batch") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public FdBatchService.BatchResult mature(@RequestParam(required=false) LocalDate businessDate){return batches.mature(businessDate);}

    @PostMapping("/api/v1/fixed-deposits/{id}/close") @Transactional
    public FdCloseResponse close(Authentication actor,@PathVariable long id,@RequestHeader("Idempotency-Key") @NotBlank @Size(max=255) String key){
        long user=userId(actor);String hash=hash(Long.toString(id));Long replay=claim("FD_CLOSE",user,key,hash);if(replay!=null)return closeResponse(replay,user);
        Map<String,Object> fd=fd(id);
        if(((Number)fd.get("owner")).longValue()!=user)throw notFound();
        if(!"MATURED".equals(fd.get("status")))throw conflict("Only a matured FD can be closed normally");
        return settle(id,user,user,key,"FD_CLOSE",fd,(BigDecimal)fd.get("maturityAmount"),"CLOSED","MATURITY_SETTLEMENT");
    }

    @PostMapping("/api/v1/fixed-deposits/{id}/premature-closure") @Transactional
    public PrematureQuote requestPremature(Authentication actor,@PathVariable long id,@RequestHeader("Idempotency-Key") @NotBlank @Size(max=255) String key,@Valid @RequestBody PrematureRequest request){
        long user=userId(actor);String reason=request.reason==null?"":request.reason.trim();String hash=hash(id+"|"+reason);Long replay=claim("PREMATURE_REQUEST",user,key,hash);if(replay!=null)return quote(replay,user);
        Map<String,Object> fd=fd(id);if(((Number)fd.get("owner")).longValue()!=user)throw notFound();if(!"ACTIVE".equals(fd.get("status")))throw conflict("Only an active FD can request premature closure");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM fd_premature_closure WHERE fpc_account_id=? AND fpc_status='PENDING'",Integer.class,id)>0)throw conflict("A premature closure request is already pending");
        long currency=((Number)fd.get("currency")).longValue();Map<String,Object> terms=jdbc.query("SELECT premature_penalty_points FROM fd_product_currency_ref WHERE product_id=? AND currency_id=?",r->r.next()?Map.of("points",r.getBigDecimal(1)==null?"":r.getBigDecimal(1).toPlainString()):null,fd.get("product"),currency);
        if(terms==null||terms.get("points")==null||terms.get("points").toString().isBlank())throw conflict("Premature closure is not configured for this product");
        BigDecimal points=new BigDecimal(terms.get("points").toString()),rate=(BigDecimal)fd.get("rate"),principal=(BigDecimal)fd.get("principal");
        BigDecimal penalized=rate.subtract(points.divide(new BigDecimal("100"),12,RoundingMode.HALF_UP)).max(BigDecimal.ZERO);
        LocalDate start=((java.sql.Timestamp)fd.get("opened")).toLocalDateTime().toLocalDate();LocalDate maturity=((java.sql.Date)fd.get("maturity")).toLocalDate();LocalDate today=clock.businessDate();LocalDate end=today.isAfter(maturity)?maturity:today;if(end.isBefore(start))end=start;
        int scale=currencyScale(currency);BigDecimal paidInterest=principal.multiply(penalized).multiply(BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(start,end))).divide(BigDecimal.valueOf(365),12,RoundingMode.HALF_UP).setScale(scale,RoundingMode.HALF_UP);
        BigDecimal fullInterest=principal.multiply(rate).multiply(BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(start,end))).divide(BigDecimal.valueOf(365),12,RoundingMode.HALF_UP).setScale(scale,RoundingMode.HALF_UP);
        BigDecimal penalty=fullInterest.subtract(paidInterest).max(BigDecimal.ZERO).setScale(scale,RoundingMode.HALF_UP),net=principal.add(paidInterest).setScale(scale,RoundingMode.HALF_UP);
        jdbc.update("INSERT INTO fd_premature_closure(fpc_account_id,fpc_closure_date,fpc_penalty_pct_applied,fpc_penalty_amount,fpc_net_payable_amount,fpc_currency_id,fpc_reason,fpc_status) VALUES(?,UTC_TIMESTAMP(6),?,?,?,?,?,'PENDING')",id,points,penalty,net,currency,reason.isBlank()?null:reason.substring(0,Math.min(reason.length(),500)));
        long closure=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);complete("PREMATURE_REQUEST",user,key,closure);return quote(closure,user);
    }

    @PostMapping("/api/v1/admin/fixed-deposits/{id}/premature-closure/{closureId}/approve") @Transactional @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public FdCloseResponse approvePremature(Authentication actor,@PathVariable long id,@PathVariable long closureId,@RequestHeader("Idempotency-Key") @NotBlank @Size(max=255) String key){
        long approver=userId(actor);String hash=hash(id+"|"+closureId);Long replay=claim("PREMATURE_APPROVAL",approver,key,hash);if(replay!=null)return closeResponse(id,approver,false);
        Map<String,Object> fd=fd(id);if(!"ACTIVE".equals(fd.get("status")))throw conflict("Only an active FD can be prematurely closed");
        Map<String,Object> q=jdbc.query("SELECT fpc_penalty_amount,fpc_net_payable_amount,fpc_currency_id,fpc_status FROM fd_premature_closure WHERE fpc_closure_id=? AND fpc_account_id=? FOR UPDATE",r->r.next()?Map.of("penalty",r.getBigDecimal(1),"net",r.getBigDecimal(2),"currency",r.getLong(3),"status",r.getString(4)):null,closureId,id);
        if(q==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Premature closure request not found");if(!"PENDING".equals(q.get("status")))throw conflict("Premature closure request is already processed");
        long owner=((Number)fd.get("owner")).longValue();if(owner==approver)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Approver must be a different staff user");
        jdbc.update("UPDATE fd_premature_closure SET fpc_status='APPROVED',fpc_approved_by=? WHERE fpc_closure_id=?",approver,closureId);
        FdCloseResponse response=settle(id,owner,approver,key,"PREMATURE_APPROVAL",fd,(BigDecimal)q.get("net"),"PREMATURELY_CLOSED","PREMATURE_SETTLEMENT");
        jdbc.update("UPDATE fd_premature_closure SET fpc_approved_by=? WHERE fpc_closure_id=?",approver,closureId);complete("PREMATURE_APPROVAL",approver,key,id);return response;
    }

    @GetMapping("/api/v1/fixed-deposits/{id}/transactions")
    public List<Map<String,Object>> transactions(Authentication actor,@PathVariable long id){Map<String,Object> fd=fd(id);if(((Number)fd.get("owner")).longValue()!=userId(actor))throw notFound();return jdbc.queryForList("SELECT record_type,record_id,occurred_at,event_type,amount,balance_after,remarks,currency_code,source_savings_account_id FROM (SELECT 'TRANSACTION' record_type,t.ft_transaction_id record_id,t.ft_txn_date occurred_at,tt.tt_txn_type_code event_type,t.ft_amount amount,t.ft_balance_after balance_after,t.ft_remarks remarks,c.fcr_iso_code currency_code,t.ft_source_savings_account_id source_savings_account_id FROM fd_transactions t JOIN transaction_types tt ON tt.tt_txn_type_id=t.ft_txn_type_id JOIN fd_currency_ref c ON c.fcr_currency_id=t.ft_currency_id WHERE t.ft_account_id=? UNION ALL SELECT 'ACCRUAL',a.fia_accrual_id,a.fia_accrual_date,'INTEREST_ACCRUAL',a.fia_accrued_amount,a.fia_cumulative_accrued,'Daily interest accrual',c.fcr_iso_code,NULL FROM fd_interest_accrual a JOIN fd_currency_ref c ON c.fcr_currency_id=a.fia_currency_id WHERE a.fia_account_id=?) history ORDER BY occurred_at,record_id",id,id);}

    @GetMapping("/api/v1/fixed-deposits/{id}/journals")
    public List<JournalView> journals(Authentication actor,@PathVariable long id){
        Map<String,Object> fd=fd(id);if(((Number)fd.get("owner")).longValue()!=userId(actor))throw notFound();
        return jdbc.query("SELECT j.fj_id,j.fj_operation,j.fj_posted_at,j.fj_savings_account_id,c.fcr_iso_code,j.fj_total_debit,j.fj_total_credit,j.fj_savings_balance_after FROM fd_journals j JOIN fd_currency_ref c ON c.fcr_currency_id=j.fj_currency_id WHERE j.fj_fd_account_id=? ORDER BY j.fj_id",
                (r,n)->new JournalView(r.getLong(1),r.getString(2),r.getTimestamp(3).toInstant().toString(),r.getLong(4),r.getString(5),r.getBigDecimal(6),r.getBigDecimal(7),r.getBigDecimal(8),jdbc.query("SELECT jl_account_code,jl_debit,jl_credit FROM fd_journal_lines WHERE jl_journal_id=? ORDER BY jl_sequence",(line,lineNumber)->new JournalLine(line.getString(1),line.getBigDecimal(2),line.getBigDecimal(3)),r.getLong(1))),id);
    }

    @GetMapping("/api/v1/fixed-deposits/{id}/accruals")
    public List<AccrualView> accruals(Authentication actor,@PathVariable long id){
        Map<String,Object> fd=fd(id);if(((Number)fd.get("owner")).longValue()!=userId(actor))throw notFound();
        return jdbc.query("SELECT a.fia_accrual_date,a.fia_accrued_amount,a.fia_cumulative_accrued,c.fcr_iso_code FROM fd_interest_accrual a JOIN fd_currency_ref c ON c.fcr_currency_id=a.fia_currency_id WHERE a.fia_account_id=? ORDER BY a.fia_accrual_date,a.fia_accrual_id",
                (r,n)->new AccrualView(id,r.getTimestamp(1).toLocalDateTime().toLocalDate().toString(),r.getBigDecimal(2),r.getBigDecimal(3),r.getString(4),(BigDecimal)fd.get("maturityAmount"),(String)fd.get("status")),id);
    }

    @GetMapping("/api/v1/fixed-deposits/{id}/accruals/cumulative")
    public AccrualView cumulativeAccrual(Authentication actor,@PathVariable long id){
        Map<String,Object> fd=fd(id);if(((Number)fd.get("owner")).longValue()!=userId(actor))throw notFound();
        return jdbc.queryForObject("SELECT COALESCE(MAX(i.fia_accrual_date),a.fa_open_date),COALESCE(MAX(i.fia_cumulative_accrued),0),c.fcr_iso_code FROM fd_accounts a JOIN fd_currency_ref c ON c.fcr_currency_id=a.fa_currency_id LEFT JOIN fd_interest_accrual i ON i.fia_account_id=a.fa_account_id WHERE a.fa_account_id=? GROUP BY a.fa_open_date,c.fcr_iso_code",
                (r,n)->new AccrualView(id,r.getTimestamp(1).toLocalDateTime().toLocalDate().toString(),BigDecimal.ZERO,r.getBigDecimal(2),r.getString(3),(BigDecimal)fd.get("maturityAmount"),(String)fd.get("status")),id);
    }

    @GetMapping("/api/v1/fixed-deposits/{id}/premature-closure")
    public List<PrematureQuote> prematureClosures(Authentication actor,@PathVariable long id){
        Map<String,Object> fd=fd(id);if(((Number)fd.get("owner")).longValue()!=userId(actor))throw notFound();
        return jdbc.query("SELECT q.fpc_closure_id,q.fpc_penalty_pct_applied,q.fpc_penalty_amount,q.fpc_net_payable_amount,q.fpc_status,q.fpc_closure_date,q.fpc_approved_by,a.fa_interest_rate-q.fpc_penalty_pct_applied/100,c.fcr_iso_code FROM fd_premature_closure q JOIN fd_accounts a ON a.fa_account_id=q.fpc_account_id JOIN fd_currency_ref c ON c.fcr_currency_id=q.fpc_currency_id WHERE q.fpc_account_id=? ORDER BY q.fpc_closure_id DESC",
                (r,n)->new PrematureQuote(r.getLong(1),id,r.getBigDecimal(2),r.getBigDecimal(3),r.getBigDecimal(4),r.getString(5),r.getTimestamp(6).toInstant().toString(),r.getObject(7)==null?null:((Number)r.getObject(7)).longValue(),r.getBigDecimal(8),r.getString(9)),id);
    }

    private FdCloseResponse settle(long id,long user,long idempotencyActor,String key,String scope,Map<String,Object> fd,BigDecimal amount,String nextStatus,String operation){
        if(amount==null)throw conflict("FD has no settlement amount");long savings=((Number)fd.get("savings")).longValue(),currency=((Number)fd.get("currency")).longValue();
        Map<String,Object> account=jdbc.query("SELECT sa_balance,sa_status FROM savings_accounts WHERE sa_account_id=? AND sa_holder_id=? AND sa_currency_id=? FOR UPDATE",r->r.next()?Map.of("balance",r.getBigDecimal(1),"status",r.getString(2)):null,savings,user,currency);
        if(account==null||!"ACTIVE".equals(account.get("status")))throw conflict("Settlement savings account is unavailable");
        int scale=currencyScale(currency);amount=amount.setScale(scale,RoundingMode.HALF_UP);BigDecimal balance=(BigDecimal)account.get("balance"),after=balance.add(amount).setScale(scale,RoundingMode.HALF_UP);
        jdbc.update("UPDATE savings_accounts SET sa_balance=?,sa_row_version=sa_row_version+1 WHERE sa_account_id=?",after,savings);
        jdbc.update("INSERT INTO savings_transactions(st_reference_id,st_account_id,st_entry_type,st_amount,st_currency_id,st_balance_after,st_remarks,st_posted_at) VALUES(?,?,'CREDIT',?,?,?,?,UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),savings,amount,currency,after,"FD settlement #"+id);
        if("PREMATURELY_CLOSED".equals(nextStatus)){
            BigDecimal penalty=jdbc.queryForObject("SELECT fpc_penalty_amount FROM fd_premature_closure WHERE fpc_account_id=? AND fpc_status='APPROVED' ORDER BY fpc_closure_id DESC LIMIT 1",BigDecimal.class,id);
            if(penalty!=null&&penalty.signum()>0)jdbc.update("INSERT INTO fd_transactions(ft_account_id,ft_txn_type_id,ft_amount,ft_currency_id,ft_remarks,ft_created_by) VALUES(?,?,?,?,?,?)",id,typeId("FD_PENALTY"),penalty,currency,"Interest foregone under premature closure rule",Long.toString(user));
        }
        jdbc.update("INSERT INTO fd_transactions(ft_account_id,ft_txn_type_id,ft_amount,ft_balance_after,ft_currency_id,ft_remarks,ft_created_by) VALUES(?,?,?,?,?,?,?)",id,typeId("FD_CLOSURE"),amount,amount,currency,"Internal FD settlement; no external payment rail was called",Long.toString(user));
        String journalKey="SETTLEMENT:"+id;
        if(!existsJournal(journalKey)){jdbc.update("INSERT INTO fd_journals(fj_key,fj_fd_account_id,fj_savings_account_id,fj_currency_id,fj_operation,fj_posted_at,fj_total_debit,fj_total_credit,fj_savings_balance_after) VALUES(?,?,?,?,'SETTLEMENT',UTC_TIMESTAMP(6),?,?,?)",journalKey,id,savings,currency,amount,amount,after);long j=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);jdbc.update("INSERT INTO fd_journal_lines(jl_journal_id,jl_sequence,jl_account_code,jl_debit,jl_credit) VALUES(?,1,'FIXED_DEPOSIT',?,0),(?,2,'SAVINGS',0,?)",j,amount,j,amount);}
        jdbc.update("UPDATE fd_accounts SET fa_status=?,fa_updated_by=?,fa_updated_date=UTC_TIMESTAMP(6),fa_row_version=fa_row_version+1 WHERE fa_account_id=?",nextStatus,Long.toString(user),id);
        event(id,nextStatus,Map.of("status",nextStatus,"settlementAmount",amount,"settledAt",clock.businessDate().toString()));complete(scope,idempotencyActor,key,id);return new FdCloseResponse(id,nextStatus,amount,after);
    }

    private Map<String,Object> fd(long id){Map<String,Object> row=jdbc.query("SELECT fa_primary_holder_id,fa_product_id,fa_principal_amount,fa_currency_id,fa_status,fa_funding_savings_account_id,fa_maturity_amount,fa_interest_rate,fa_open_date,fa_maturity_date FROM fd_accounts WHERE fa_account_id=? FOR UPDATE",r->r.next()?Map.of("owner",r.getLong(1),"product",r.getLong(2),"principal",r.getBigDecimal(3),"currency",r.getLong(4),"status",r.getString(5),"savings",r.getLong(6),"maturityAmount",r.getBigDecimal(7)==null?BigDecimal.ZERO:r.getBigDecimal(7),"rate",r.getBigDecimal(8),"opened",r.getTimestamp(9),"maturity",r.getDate(10)):null,id);if(row==null)throw notFound();return row;}
    private PrematureQuote quote(long closure,long user){return jdbc.query("SELECT c.fpc_account_id,c.fpc_penalty_pct_applied,c.fpc_penalty_amount,c.fpc_net_payable_amount,c.fpc_status,c.fpc_closure_date,c.fpc_approved_by,a.fa_interest_rate-c.fpc_penalty_pct_applied/100,cr.fcr_iso_code FROM fd_premature_closure c JOIN fd_accounts a ON a.fa_account_id=c.fpc_account_id JOIN fd_currency_ref cr ON cr.fcr_currency_id=c.fpc_currency_id WHERE c.fpc_closure_id=? AND a.fa_primary_holder_id=?",r->{if(!r.next())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Premature closure request not found");return new PrematureQuote(closure,r.getLong(1),r.getBigDecimal(2),r.getBigDecimal(3),r.getBigDecimal(4),r.getString(5),r.getTimestamp(6).toInstant().toString(),r.getObject(7)==null?null:((Number)r.getObject(7)).longValue(),r.getBigDecimal(8),r.getString(9));},closure,user);}
    private FdCloseResponse closeResponse(long id,long user){return closeResponse(id,user,true);}
    private FdCloseResponse closeResponse(long id,long user,boolean enforceOwner){String sql="SELECT fa_status,fa_maturity_amount,sa_balance FROM fd_accounts a JOIN savings_accounts s ON s.sa_account_id=a.fa_funding_savings_account_id WHERE a.fa_account_id=?"+(enforceOwner?" AND a.fa_primary_holder_id=?":"");Map<String,Object> r=enforceOwner?jdbc.query(sql,rs->rs.next()?Map.of("status",rs.getString(1),"amount",rs.getBigDecimal(2),"balance",rs.getBigDecimal(3)):null,id,user):jdbc.query(sql,rs->rs.next()?Map.of("status",rs.getString(1),"amount",rs.getBigDecimal(2),"balance",rs.getBigDecimal(3)):null,id);if(r==null)throw notFound();return new FdCloseResponse(id,(String)r.get("status"),(BigDecimal)r.get("amount"),(BigDecimal)r.get("balance"));}
    private Long claim(String scope,long actor,String key,String hash){try{jdbc.update("INSERT INTO fd_operation_idempotency(foi_operation_scope,foi_actor_key,foi_idempotency_key,foi_request_hash,foi_status) VALUES(?,?,?,?,'PENDING')",scope,Long.toString(actor),key,hash);return null;}catch(DuplicateKeyException e){Map<String,Object> r=jdbc.queryForMap("SELECT foi_request_hash,foi_status,foi_aggregate_id FROM fd_operation_idempotency WHERE foi_operation_scope=? AND foi_actor_key=? AND foi_idempotency_key=?",scope,Long.toString(actor),key);if(!hash.equals(r.get("foi_request_hash")))throw conflict("Idempotency key was used for a different operation");if("COMPLETED".equals(r.get("foi_status"))&&r.get("foi_aggregate_id")!=null)return ((Number)r.get("foi_aggregate_id")).longValue();throw conflict("Operation is already in progress");}}
    private void complete(String scope,long actor,String key,long aggregate){jdbc.update("UPDATE fd_operation_idempotency SET foi_status='COMPLETED',foi_aggregate_id=?,foi_response_reference=?,foi_completed_at=UTC_TIMESTAMP(6) WHERE foi_operation_scope=? AND foi_actor_key=? AND foi_idempotency_key=?",aggregate,Long.toString(aggregate),scope,Long.toString(actor),key);}
    private long userId(Authentication a){Object x=a.getDetails() instanceof Map<?,?> d?d.get("userId"):null;if(!(x instanceof Number n))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Token has no user identity");return n.longValue();}
    private int currencyScale(long id){Integer n=jdbc.queryForObject("SELECT fcr_decimal_places FROM fd_currency_ref WHERE fcr_currency_id=?",Integer.class,id);return n==null?2:n;}
    private long typeId(String code){return jdbc.queryForObject("SELECT tt_txn_type_id FROM transaction_types WHERE tt_txn_type_code=?",Long.class,code);}
    private boolean existsJournal(String key){return Boolean.TRUE.equals(jdbc.query("SELECT fj_id FROM fd_journals WHERE fj_key=?",(org.springframework.jdbc.core.ResultSetExtractor<Boolean>)r->r.next(),key));}
    private void event(long id,String type,Object payload){try{jdbc.update("INSERT INTO fd_account_events(fae_account_id,fae_event_type,fae_payload,fae_aggregate_version,fae_created_by) VALUES(?,?,CAST(? AS JSON),COALESCE((SELECT fa_row_version FROM fd_accounts WHERE fa_account_id=?),0),?)",id,type,json.writeValueAsString(payload),id,"fd-workflow");}catch(Exception e){throw new IllegalStateException(e);}}
    private String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Fixed deposit not found");}
    private ResponseStatusException conflict(String s){return new ResponseStatusException(HttpStatus.CONFLICT,s);}

    public record PrematureRequest(@Size(max=500) String reason){}
    public record PrematureQuote(@com.fasterxml.jackson.annotation.JsonProperty("closure_id") long closureId,@com.fasterxml.jackson.annotation.JsonProperty("account_id") long accountId,@com.fasterxml.jackson.annotation.JsonProperty("penalty_points") BigDecimal penaltyPoints,@com.fasterxml.jackson.annotation.JsonProperty("penalty_amount") BigDecimal penaltyAmount,@com.fasterxml.jackson.annotation.JsonProperty("net_payable_amount") BigDecimal netPayableAmount,String status,@com.fasterxml.jackson.annotation.JsonProperty("requested_date") String requestedDate,@com.fasterxml.jackson.annotation.JsonProperty("approved_by") Long approvedBy,@com.fasterxml.jackson.annotation.JsonProperty("penalized_rate") BigDecimal penalizedRate,@com.fasterxml.jackson.annotation.JsonProperty("currency_code") String currencyCode){}
    public record JournalView(@com.fasterxml.jackson.annotation.JsonProperty("journal_id") long journalId,String operation,@com.fasterxml.jackson.annotation.JsonProperty("posted_at") String postedAt,@com.fasterxml.jackson.annotation.JsonProperty("savings_account_id") long savingsAccountId,@com.fasterxml.jackson.annotation.JsonProperty("currency_code") String currencyCode,@com.fasterxml.jackson.annotation.JsonProperty("total_debit") BigDecimal totalDebit,@com.fasterxml.jackson.annotation.JsonProperty("total_credit") BigDecimal totalCredit,@com.fasterxml.jackson.annotation.JsonProperty("savings_balance_after") BigDecimal savingsBalanceAfter,List<JournalLine> lines){}
    public record JournalLine(@com.fasterxml.jackson.annotation.JsonProperty("account_code") String accountCode,BigDecimal debit,BigDecimal credit){}
    public record AccrualView(@com.fasterxml.jackson.annotation.JsonProperty("account_id") long accountId,@com.fasterxml.jackson.annotation.JsonProperty("accrual_date") String accrualDate,@com.fasterxml.jackson.annotation.JsonProperty("accrued_amount") BigDecimal accruedAmount,@com.fasterxml.jackson.annotation.JsonProperty("cumulative_accrued") BigDecimal cumulativeAccrued,@com.fasterxml.jackson.annotation.JsonProperty("currency_code") String currencyCode,@com.fasterxml.jackson.annotation.JsonProperty("maturity_amount") BigDecimal maturityAmount,String status){}
    public record FdCloseResponse(long accountId,String status,BigDecimal settledAmount,BigDecimal savingsBalanceAfter){}
}
