package com.bank.fdservice;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class StaffSavingsMovementService {
    private final JdbcTemplate jdbc;private final PostingService postings;
    public StaffSavingsMovementService(JdbcTemplate jdbc,PostingService postings){this.jdbc=jdbc;this.postings=postings;}
    @Transactional
    public StaffAccountController.SavingsRow transact(String actor,long id,String key,StaffAccountController.MoneyRequest request,boolean credit){
        if (!credit) validateAttestation(request);
        String scope=credit?"STAFF_SAVINGS_DEPOSIT":"STAFF_SAVINGS_WITHDRAWAL";
        String hash=hash(id+"|"+request.amount().toPlainString()+"|"+(request.remarks()==null?"":request.remarks())+(credit?"":"|"+request.verificationMethod()+"|"+request.verificationReference()));
        try{jdbc.update("INSERT INTO fd_operation_idempotency(foi_operation_scope,foi_actor_key,foi_idempotency_key,foi_request_hash,foi_status) VALUES(?,?,?,?,'PENDING')",scope,actor,key,hash);}
        catch(DuplicateKeyException e){Map<String,Object> existing=jdbc.queryForMap("SELECT foi_request_hash,foi_status FROM fd_operation_idempotency WHERE foi_operation_scope=? AND foi_actor_key=? AND foi_idempotency_key=?",scope,actor,key);if(!hash.equals(existing.get("foi_request_hash")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key was already used for a different transaction");if("COMPLETED".equals(existing.get("foi_status")))return savingsRow(id);throw new ResponseStatusException(HttpStatus.CONFLICT,"Transaction is already in progress");}

        Map<String,Object> account=jdbc.query("SELECT sa_currency_id,sa_balance,sa_status FROM savings_accounts WHERE sa_account_id=? FOR UPDATE",rs->rs.next()?Map.of("currency",rs.getLong(1),"balance",rs.getBigDecimal(2),"status",rs.getString(3)):null,id);
        if(account==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Savings account not found");if(!"ACTIVE".equals(account.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Savings account is not active");
        long currencyId=((Number)account.get("currency")).longValue();int scale=jdbc.queryForObject("SELECT fcr_decimal_places FROM fd_currency_ref WHERE fcr_currency_id=?",Integer.class,currencyId);BigDecimal amount;
        try{amount=request.amount().setScale(scale,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Amount exceeds the currency decimal scale");}
        String reference="STAFF-"+UUID.randomUUID();postings.postSavingsMovement(id,currencyId,amount,credit?"CREDIT":"DEBIT",reference,request.remarks()==null?"Staff teller transaction":request.remarks().trim(),actor,(credit?"TELLER_DEPOSIT:":"TELLER_WITHDRAWAL:")+reference,credit?"TELLER_DEPOSIT":"TELLER_WITHDRAWAL",credit?"CASH_TELLER":"SAVINGS",credit?"SAVINGS":"CASH_TELLER");
        if (!credit) {
            long ownerId=jdbc.queryForObject("SELECT sa_holder_id FROM savings_accounts WHERE sa_account_id=?",Long.class,id);
            jdbc.update("INSERT INTO fd_staff_withdrawal_audit(savings_account_id,customer_user_id,staff_username,amount,currency_id,reference,verification_method,verification_reference,remarks) VALUES(?,?,?,?,?,?,?,?,?)",
                    id,ownerId,actor,amount,currencyId,reference,request.verificationMethod().name(),request.verificationReference(),request.remarks().trim());
        }
        jdbc.update("UPDATE fd_operation_idempotency SET foi_status='COMPLETED',foi_aggregate_id=?,foi_response_reference=?,foi_completed_at=UTC_TIMESTAMP(6) WHERE foi_operation_scope=? AND foi_actor_key=? AND foi_idempotency_key=?",id,reference,scope,actor,key);return savingsRow(id);
    }
    private void validateAttestation(StaffAccountController.MoneyRequest request) {
        if (request.verificationMethod()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose an identity verification method.");
        String reference=request.verificationReference(), remarks=request.remarks();
        if (reference!=null && !reference.matches("[A-Za-z0-9/-]{0,40}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Verification reference must contain at most 40 letters, digits, slashes or hyphens.");
        if (remarks==null || remarks.trim().length()<5 || remarks.trim().length()>140 || remarks.codePoints().anyMatch(Character::isISOControl))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a withdrawal remark of 5-140 characters.");
    }
    private StaffAccountController.SavingsRow savingsRow(long id){return jdbc.query("SELECT s.sa_account_id,s.sa_account_number,s.sa_display_name,s.sa_balance,c.fcr_iso_code,c.fcr_decimal_places,s.sa_status,s.sa_created_date,u.fur_full_name,s.sa_holder_id,(SELECT COUNT(*) FROM savings_transactions t WHERE t.st_account_id=s.sa_account_id) FROM savings_accounts s JOIN fd_currency_ref c ON c.fcr_currency_id=s.sa_currency_id LEFT JOIN fd_user_ref u ON u.fur_user_id=s.sa_holder_id WHERE s.sa_account_id=?",rs->{if(!rs.next())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Savings account not found");return new StaffAccountController.SavingsRow(rs.getLong(1),mask(rs.getString(2)),rs.getString(3),rs.getBigDecimal(4),rs.getString(5),rs.getInt(6),rs.getString(7),rs.getTimestamp(8).toInstant().toString(),rs.getString(9),rs.getLong(10),rs.getLong(11));},id);}
    private static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String mask(String s){return s.length()<=4?"••••":"••••"+s.substring(s.length()-4);}
}
