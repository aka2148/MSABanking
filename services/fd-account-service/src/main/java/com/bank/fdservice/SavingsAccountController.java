package com.bank.fdservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/savings-accounts")
public class SavingsAccountController {
    private final JdbcTemplate jdbc;
    private final boolean demoOpeningEnabled;
    public SavingsAccountController(JdbcTemplate jdbc,@Value("${app.demo.opening-balance.enabled:false}") boolean demoOpeningEnabled){this.jdbc=jdbc;this.demoOpeningEnabled=demoOpeningEnabled;}

    @GetMapping("/currencies")
    public List<SavingsCurrencyView> currencies(){
        return jdbc.query("SELECT fcr_iso_code,fcr_currency_name,fcr_decimal_places FROM fd_currency_ref ORDER BY fcr_iso_code",
                (rs,n)->new SavingsCurrencyView(rs.getString(1),rs.getString(2),rs.getInt(3)));
    }

    @GetMapping
    public List<SavingsAccountView> mine(Authentication actor){
        long userId=userId(actor);
        return jdbc.query("SELECT s.sa_account_id,s.sa_account_number,s.sa_display_name,s.sa_currency_id,c.fcr_iso_code,s.sa_balance,s.sa_status,s.sa_created_date FROM savings_accounts s JOIN fd_currency_ref c ON c.fcr_currency_id=s.sa_currency_id WHERE s.sa_holder_id=? ORDER BY s.sa_account_id",
                (rs,n)->view(rs),userId);
    }

    @GetMapping("/{id}")
    public SavingsAccountView getMine(Authentication actor,@PathVariable long id){return find(id,userId(actor));}

    @GetMapping("/demo-options")
    public DemoOptions demoOptions(){return new DemoOptions(demoOpeningEnabled);}

    @PostMapping("/{id}/demo-opening") @Transactional
    public SavingsAccountView demoOpening(Authentication actor,@PathVariable long id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max=255) String key,
            @Valid @RequestBody DemoOpeningRequest request){
        long user=userId(actor);
        if(!demoOpeningEnabled)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Demo opening credits are disabled");
        String requestHash=hash(id+"|"+request.amount.toPlainString());
        try{jdbc.update("INSERT INTO fd_operation_idempotency(foi_operation_scope,foi_actor_key,foi_idempotency_key,foi_request_hash,foi_status) VALUES('SAVINGS_DEMO_OPENING',?,?,?,'PENDING')",Long.toString(user),key,requestHash);}
        catch(DuplicateKeyException e){Map<String,Object> existing=jdbc.queryForMap("SELECT foi_request_hash,foi_status,foi_aggregate_id FROM fd_operation_idempotency WHERE foi_operation_scope='SAVINGS_DEMO_OPENING' AND foi_actor_key=? AND foi_idempotency_key=?",Long.toString(user),key);if(!requestHash.equals(existing.get("foi_request_hash")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key was already used for a different opening-credit request");if("COMPLETED".equals(existing.get("foi_status"))&&existing.get("foi_aggregate_id")!=null)return find(((Number)existing.get("foi_aggregate_id")).longValue(),user);throw new ResponseStatusException(HttpStatus.CONFLICT,"Opening-credit request is already in progress");}
        Map<String,Object> account=jdbc.query("SELECT sa_currency_id,sa_balance,sa_status FROM savings_accounts WHERE sa_account_id=? AND sa_holder_id=? FOR UPDATE",rs->rs.next()?Map.of("currency",rs.getLong(1),"balance",rs.getBigDecimal(2),"status",rs.getString(3)):null,id,user);
        if(account==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Savings account not found");
        if(!"ACTIVE".equals(account.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Savings account is not active");
        if(((BigDecimal)account.get("balance")).signum()!=0||jdbc.queryForObject("SELECT COUNT(*) FROM savings_transactions WHERE st_account_id=?",Integer.class,id)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"A demo opening credit can be recorded only once on an unused savings account");
        long currency=((Number)account.get("currency")).longValue();Integer scale=jdbc.queryForObject("SELECT fcr_decimal_places FROM fd_currency_ref WHERE fcr_currency_id=?",Integer.class,currency);int decimals=scale==null?2:scale;
        BigDecimal amount;try{amount=request.amount.setScale(decimals,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Opening credit exceeds the currency decimal scale");}
        jdbc.update("UPDATE savings_accounts SET sa_balance=?,sa_row_version=sa_row_version+1 WHERE sa_account_id=?",amount,id);
        jdbc.update("INSERT INTO savings_transactions(st_reference_id,st_account_id,st_entry_type,st_amount,st_currency_id,st_balance_after,st_remarks,st_posted_at) VALUES(?,?,'CREDIT',?,?,?,?,UTC_TIMESTAMP(6))","DEMO-OPENING:"+UUID.randomUUID(),id,amount,currency,amount,"Demo opening credit only; no external payment was processed");
        jdbc.update("UPDATE fd_operation_idempotency SET foi_status='COMPLETED',foi_aggregate_id=?,foi_response_reference=?,foi_completed_at=UTC_TIMESTAMP(6) WHERE foi_operation_scope='SAVINGS_DEMO_OPENING' AND foi_actor_key=? AND foi_idempotency_key=?",id,Long.toString(id),Long.toString(user),key);
        return find(id,user);
    }

    @GetMapping("/{id}/transactions")
    public List<java.util.Map<String,Object>> transactions(Authentication actor,@PathVariable long id){
        Long owner=jdbc.query("SELECT sa_holder_id FROM savings_accounts WHERE sa_account_id=?",rs->rs.next()?rs.getLong(1):null,id);
        if(owner==null||owner!=userId(actor))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Savings account not found");
        return jdbc.queryForList("SELECT st_id,st_reference_id,st_entry_type,st_amount,st_currency_id,st_balance_after,st_remarks,st_posted_at FROM savings_transactions WHERE st_account_id=? ORDER BY st_posted_at,st_id",id);
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    public SavingsAccountView openMine(Authentication actor,@Valid @RequestBody OpenAccountRequest request){
        return create(userId(actor),request);
    }

    @PostMapping("/admin") @ResponseStatus(HttpStatus.CREATED) @Transactional @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public SavingsAccountView openForCustomer(@Valid @RequestBody AdminOpenAccountRequest request){
        return create(request.userId,new OpenAccountRequest(request.currencyCode,request.displayName));
    }

    private SavingsAccountView create(long ownerId,OpenAccountRequest request){
        Integer owner=jdbc.query("SELECT 1 FROM fd_user_ref WHERE fur_user_id=? AND fur_user_status='ACTIVE'",rs->rs.next()?1:null,ownerId);
        if(owner==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Customer reference is not available in the FD service yet");
        Long currencyId=jdbc.query("SELECT fcr_currency_id FROM fd_currency_ref WHERE UPPER(fcr_iso_code)=UPPER(?)",rs->rs.next()?rs.getLong(1):null,request.currencyCode.trim());
        if(currencyId==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Currency is not available in the FD service yet");
        String accountNumber="SA-"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase();
        jdbc.update("INSERT INTO savings_accounts(sa_account_number,sa_holder_id,sa_currency_id,sa_display_name,sa_balance,sa_status,sa_created_date) VALUES(?,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6))",accountNumber,ownerId,currencyId,request.displayName.trim(),BigDecimal.ZERO);
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        return find(id,ownerId);
    }

    private SavingsAccountView find(long id,long owner){return jdbc.query("SELECT s.sa_account_id,s.sa_account_number,s.sa_display_name,s.sa_currency_id,c.fcr_iso_code,s.sa_balance,s.sa_status,s.sa_created_date FROM savings_accounts s JOIN fd_currency_ref c ON c.fcr_currency_id=s.sa_currency_id WHERE s.sa_account_id=? AND s.sa_holder_id=?",rs->{if(!rs.next())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Savings account not found");return view(rs);},id,owner);}
    private SavingsAccountView view(java.sql.ResultSet rs)throws java.sql.SQLException{return new SavingsAccountView(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getBigDecimal(6),rs.getString(7),rs.getTimestamp(8).toInstant().toString());}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}

    private long userId(Authentication actor){
        Object value=actor.getDetails() instanceof java.util.Map<?,?> details?details.get("userId"):null;
        if(!(value instanceof Number number)||number.longValue()<=0)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Token does not contain a valid customer identity");
        return number.longValue();
    }

    public record OpenAccountRequest(@NotBlank @Size(max=10) String currencyCode,@NotBlank @Size(max=80) String displayName){}
    public record SavingsCurrencyView(String currencyCode,String currencyName,int decimalPlaces){}
    public record AdminOpenAccountRequest(long userId,@NotBlank @Size(max=10) String currencyCode,@NotBlank @Size(max=80) String displayName){}
    public record DemoOptions(boolean enabled){}
    public record DemoOpeningRequest(@NotNull @DecimalMin("0.01") BigDecimal amount){}
    public record SavingsAccountView(@com.fasterxml.jackson.annotation.JsonProperty("account_id") long accountId,@com.fasterxml.jackson.annotation.JsonProperty("account_number") String accountNumber,@com.fasterxml.jackson.annotation.JsonProperty("display_name") String displayName,long currencyId,@com.fasterxml.jackson.annotation.JsonProperty("currency_code") String currencyCode,BigDecimal balance,String status,@com.fasterxml.jackson.annotation.JsonProperty("created_date") String createdDate){}
}
