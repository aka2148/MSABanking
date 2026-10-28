package com.bank.fdservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/current-accounts")
public class CurrentAccountController {
    private final JdbcTemplate jdbc; private final DepositProductOfferClient offers; private final ObjectMapper json; private final DemoClockService clock;
    public CurrentAccountController(JdbcTemplate jdbc,DepositProductOfferClient offers,ObjectMapper json,DemoClockService clock){this.jdbc=jdbc;this.offers=offers;this.json=json;this.clock=clock;}

    @GetMapping public List<AccountView> mine(Authentication actor){return list(userId(actor));}
    @GetMapping("/{id}") public AccountView detail(Authentication actor,@PathVariable long id){return find(id,userId(actor));}
    @GetMapping("/{id}/transactions") public List<Map<String,Object>> history(Authentication actor,@PathVariable long id){find(id,userId(actor));return jdbc.queryForList("SELECT st_id,st_reference_id,st_entry_type,st_amount,st_currency_id,st_balance_after,st_remarks,st_posted_at FROM savings_transactions WHERE st_account_id=? ORDER BY st_posted_at,st_id",id);}

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    public AccountView open(Authentication actor,HttpServletRequest http,@RequestHeader("Idempotency-Key") @NotBlank @Size(max=255) String key,@Valid @RequestBody OpenRequest request){
        long owner=userId(actor);CustomerProductAccess.requireVerified(owner);return create(owner,request,"CUSTOMER:"+owner,key,http.getHeader("Authorization"));
    }
    @PostMapping("/admin") @ResponseStatus(HttpStatus.CREATED) @Transactional @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public AccountView openForCustomer(Authentication actor,HttpServletRequest http,@RequestHeader("Idempotency-Key") @NotBlank @Size(max=255) String key,@Valid @RequestBody AdminOpenRequest request){
        return create(request.userId(),new OpenRequest(request.productId(),request.currencyCode(),request.displayName()),"STAFF:"+actor.getName(),key,http.getHeader("Authorization"));
    }
    private AccountView create(long owner,OpenRequest request,String actorKey,String key,String authorization){
        String currency=request.currencyCode().trim().toUpperCase(java.util.Locale.ROOT), name=request.displayName().trim();
        String hash=hash(owner+"|"+request.productId()+"|"+currency+"|"+name);
        try{jdbc.update("INSERT INTO fd_operation_idempotency(foi_operation_scope,foi_actor_key,foi_idempotency_key,foi_request_hash,foi_status) VALUES('CURRENT_ACCOUNT_OPENING',?,?,?,'PENDING')",actorKey,key,hash);}
        catch(DuplicateKeyException e){Map<String,Object> row=jdbc.queryForMap("SELECT foi_request_hash,foi_status,foi_aggregate_id FROM fd_operation_idempotency WHERE foi_operation_scope='CURRENT_ACCOUNT_OPENING' AND foi_actor_key=? AND foi_idempotency_key=?",actorKey,key);if(!hash.equals(row.get("foi_request_hash")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key was already used for a different account request");if("COMPLETED".equals(row.get("foi_status"))&&row.get("foi_aggregate_id")!=null)return find(((Number)row.get("foi_aggregate_id")).longValue(),owner);throw new ResponseStatusException(HttpStatus.CONFLICT,"Account request is already in progress");}
        Integer active=jdbc.query("SELECT 1 FROM fd_user_ref WHERE fur_user_id=? AND fur_user_status='ACTIVE'",rs->rs.next()?1:null,owner);if(active==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Customer reference is synchronizing. Retry shortly.");
        JsonNode offer=offers.get("CURRENT",request.productId(),authorization);
        JsonNode currencyTerms=null;for(JsonNode c:offer.path("currencies"))if(currency.equalsIgnoreCase(c.path("code").asText()))currencyTerms=c;
        if(currencyTerms==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Currency is not configured for this product");
        Long currencyId=jdbc.query("SELECT fcr_currency_id FROM fd_currency_ref WHERE UPPER(fcr_iso_code)=?",rs->rs.next()?rs.getLong(1):null,currency);if(currencyId==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Currency reference has not arrived from Product Service");
        BigDecimal min=offer.path("minimumBalance").decimalValue();JsonNode allBands=offer.path("interestBands");var bands=new java.util.ArrayList<JsonNode>();for(JsonNode b:allBands)if(currency.equalsIgnoreCase(b.path("currencyCode").asText()))bands.add(b.deepCopy());
        String terms;try{terms=json.writeValueAsString(bands);}catch(Exception e){throw new IllegalStateException("Could not freeze account product terms",e);}
        LocalDate opened=clock.businessDate();String number="CA-"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase(java.util.Locale.ROOT);
        jdbc.update("INSERT INTO savings_accounts(sa_account_number,sa_holder_id,sa_currency_id,sa_display_name,sa_balance,sa_status,sa_created_date,sa_account_type,sa_product_id,sa_product_code,sa_product_name,sa_minimum_balance,sa_interest_terms,sa_interest_start_date) VALUES(?,?,?,?,0,'ACTIVE',UTC_TIMESTAMP(6),'CURRENT',?,?,?,?,CAST(? AS JSON),?)",number,owner,currencyId,name,request.productId(),offer.path("productCode").asText(),offer.path("productName").asText(),min,terms,opened);
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);complete(actorKey,key,id);return find(id,owner);
    }
    private void complete(String actor,String key,long id){jdbc.update("UPDATE fd_operation_idempotency SET foi_status='COMPLETED',foi_aggregate_id=?,foi_response_reference=?,foi_completed_at=UTC_TIMESTAMP(6) WHERE foi_operation_scope='CURRENT_ACCOUNT_OPENING' AND foi_actor_key=? AND foi_idempotency_key=?",id,Long.toString(id),actor,key);}
    private List<AccountView> list(long owner){return jdbc.query("SELECT s.sa_account_id,s.sa_account_number,s.sa_display_name,s.sa_currency_id,c.fcr_iso_code,s.sa_balance,s.sa_status,s.sa_created_date,s.sa_product_id,s.sa_product_code,s.sa_product_name,s.sa_minimum_balance FROM savings_accounts s JOIN fd_currency_ref c ON c.fcr_currency_id=s.sa_currency_id WHERE s.sa_holder_id=? AND s.sa_account_type='CURRENT' AND s.sa_status<>'CLOSED' ORDER BY s.sa_account_id",(r,n)->view(r),owner);}
    private AccountView find(long id,long owner){return jdbc.query("SELECT s.sa_account_id,s.sa_account_number,s.sa_display_name,s.sa_currency_id,c.fcr_iso_code,s.sa_balance,s.sa_status,s.sa_created_date,s.sa_product_id,s.sa_product_code,s.sa_product_name,s.sa_minimum_balance FROM savings_accounts s JOIN fd_currency_ref c ON c.fcr_currency_id=s.sa_currency_id WHERE s.sa_account_id=? AND s.sa_holder_id=? AND s.sa_account_type='CURRENT'",r->{if(!r.next())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Current account not found");return view(r);},id,owner);}
    private AccountView view(java.sql.ResultSet r)throws java.sql.SQLException{BigDecimal balance=r.getBigDecimal(6),min=r.getBigDecimal(12);return new AccountView(r.getLong(1),r.getString(2),r.getString(3),r.getLong(4),r.getString(5),balance,r.getString(7),r.getTimestamp(8).toInstant().toString(),"CURRENT",(Long)r.getObject(9),r.getString(10),r.getString(11),min,balance.subtract(min).max(BigDecimal.ZERO));}
    private long userId(Authentication actor){Object v=actor.getDetails() instanceof Map<?,?> d?d.get("userId"):null;if(!(v instanceof Number n)||n.longValue()<=0)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Token does not contain a valid customer identity");return n.longValue();}
    private String hash(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public record OpenRequest(@NotNull @Positive Long productId,@NotBlank @Size(max=10) String currencyCode,@NotBlank @Size(max=80) String displayName){}
    public record AdminOpenRequest(@NotNull @Positive Long userId,@NotNull @Positive Long productId,@NotBlank @Size(max=10) String currencyCode,@NotBlank @Size(max=80) String displayName){}
    public record AccountView(@com.fasterxml.jackson.annotation.JsonProperty("account_id") long accountId,@com.fasterxml.jackson.annotation.JsonProperty("account_number") String accountNumber,@com.fasterxml.jackson.annotation.JsonProperty("display_name") String displayName,long currencyId,@com.fasterxml.jackson.annotation.JsonProperty("currency_code") String currencyCode,BigDecimal balance,String status,@com.fasterxml.jackson.annotation.JsonProperty("created_date") String createdDate,String accountType,Long productId,String productCode,String productName,BigDecimal minimumBalance,BigDecimal availableBalance){}
}
