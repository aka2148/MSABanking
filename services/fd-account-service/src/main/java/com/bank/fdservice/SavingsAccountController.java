package com.bank.fdservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final PostingService postings;
    private final DepositProductOfferClient offers;
    private final ObjectMapper json;
    private final DemoClockService clock;
    private final boolean demoOpeningEnabled;
    private final BigDecimal demoOpeningMaximum;
    public SavingsAccountController(JdbcTemplate jdbc,PostingService postings,DepositProductOfferClient offers,ObjectMapper json,DemoClockService clock,@Value("${app.demo.opening-balance.enabled:false}") boolean demoOpeningEnabled,@Value("${app.demo.opening-max-amount:}") String demoOpeningMaximum){this.jdbc=jdbc;this.postings=postings;this.offers=offers;this.json=json;this.clock=clock;this.demoOpeningEnabled=demoOpeningEnabled;this.demoOpeningMaximum=demoOpeningMaximum==null||demoOpeningMaximum.isBlank()?null:new BigDecimal(demoOpeningMaximum);}

    @GetMapping("/currencies")
    public List<SavingsCurrencyView> currencies(){
        return jdbc.query("SELECT fcr_iso_code,fcr_currency_name,fcr_decimal_places FROM fd_currency_ref ORDER BY fcr_iso_code",
                (rs,n)->new SavingsCurrencyView(rs.getString(1),rs.getString(2),rs.getInt(3)));
    }

    @GetMapping
    public List<SavingsAccountView> mine(Authentication actor,@RequestParam(required=false) String type,@RequestParam(defaultValue="false") boolean includeClosed){
        long userId=userId(actor);
        String normalized=type==null||type.isBlank()?null:type.trim().toUpperCase(java.util.Locale.ROOT);if(normalized!=null&&!List.of("SAVINGS","CURRENT").contains(normalized))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown deposit account type");
        String sql="SELECT s.sa_account_id,s.sa_account_number,s.sa_display_name,s.sa_currency_id,c.fcr_iso_code,s.sa_balance,s.sa_status,s.sa_created_date,s.sa_account_type,s.sa_product_id,s.sa_product_code,s.sa_product_name,s.sa_minimum_balance FROM savings_accounts s JOIN fd_currency_ref c ON c.fcr_currency_id=s.sa_currency_id WHERE s.sa_holder_id=?"+(normalized==null?"":" AND s.sa_account_type=?")+(includeClosed?"":" AND s.sa_status<>'CLOSED'")+" ORDER BY s.sa_account_id";
        return normalized==null?jdbc.query(sql,(rs,n)->view(rs),userId):jdbc.query(sql,(rs,n)->view(rs),userId,normalized);
    }

    @GetMapping("/{id}")
    public SavingsAccountView getMine(Authentication actor,@PathVariable long id){return find(id,userId(actor));}

    @GetMapping("/demo-options") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public DemoOptions demoOptions(){return new DemoOptions(demoOpeningEnabled);}

    @PostMapping("/{id}/demo-opening") @Transactional @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public SavingsAccountView demoOpening(Authentication actor,@PathVariable long id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max=255) String key,
            @Valid @RequestBody DemoOpeningRequest request){
        String actorKey=actor.getName();
        requireDemoOpeningAuthorization(actor,id);
        String requestHash=hash(id+"|"+request.amount.toPlainString());
        try{jdbc.update("INSERT INTO fd_operation_idempotency(foi_operation_scope,foi_actor_key,foi_idempotency_key,foi_request_hash,foi_status) VALUES('SAVINGS_DEMO_OPENING',?,?,?,'PENDING')",actorKey,key,requestHash);}
            catch(DuplicateKeyException e){Map<String,Object> existing=jdbc.queryForMap("SELECT foi_request_hash,foi_status,foi_aggregate_id FROM fd_operation_idempotency WHERE foi_operation_scope='SAVINGS_DEMO_OPENING' AND foi_actor_key=? AND foi_idempotency_key=?",actorKey,key);if(!requestHash.equals(existing.get("foi_request_hash")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key was already used for a different opening-credit request");if("COMPLETED".equals(existing.get("foi_status"))&&existing.get("foi_aggregate_id")!=null)return findAny(((Number)existing.get("foi_aggregate_id")).longValue());throw new ResponseStatusException(HttpStatus.CONFLICT,"Opening-credit request is already in progress");}
        Map<String,Object> account=jdbc.query("SELECT sa_currency_id,sa_balance,sa_status FROM savings_accounts WHERE sa_account_id=? FOR UPDATE",rs->rs.next()?Map.of("currency",rs.getLong(1),"balance",rs.getBigDecimal(2),"status",rs.getString(3)):null,id);
        if(account==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Savings account not found");
        if(!"ACTIVE".equals(account.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Savings account is not active");
        if(((BigDecimal)account.get("balance")).signum()!=0||jdbc.queryForObject("SELECT COUNT(*) FROM savings_transactions WHERE st_account_id=?",Integer.class,id)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"A demo opening credit can be recorded only once on an unused savings account");
        long currency=((Number)account.get("currency")).longValue();Integer scale=jdbc.queryForObject("SELECT fcr_decimal_places FROM fd_currency_ref WHERE fcr_currency_id=?",Integer.class,currency);int decimals=scale==null?2:scale;
        BigDecimal amount;try{amount=request.amount.setScale(decimals,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Opening credit exceeds the currency decimal scale");}
        if(demoOpeningMaximum!=null&&amount.compareTo(demoOpeningMaximum)>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Opening credit exceeds the configured demonstration maximum");
        postings.postSavingsMovement(id,currency,amount,"CREDIT","DEMO_OPENING:"+id,"Demo opening credit only; no external payment was processed",actorKey,"DEMO_OPENING:"+id,"DEMO_OPENING","DEMO_SUSPENSE","SAVINGS");
        jdbc.update("UPDATE fd_operation_idempotency SET foi_status='COMPLETED',foi_aggregate_id=?,foi_response_reference=?,foi_completed_at=UTC_TIMESTAMP(6) WHERE foi_operation_scope='SAVINGS_DEMO_OPENING' AND foi_actor_key=? AND foi_idempotency_key=?",id,Long.toString(id),actorKey,key);
        return findAny(id);
    }

    private void requireDemoOpeningAuthorization(Authentication actor,long accountId){
        if(actor==null||actor.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_BANK_OFFICER")||a.getAuthority().equals("ROLE_ADMIN")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Staff authorization required");
        if(!demoOpeningEnabled)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Demo opening credits are disabled");
        Integer exists=jdbc.query("SELECT 1 FROM savings_accounts WHERE sa_account_id=?",rs->rs.next()?1:null,accountId);
        if(exists==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Savings account not found");
    }

    @GetMapping("/{id}/transactions")
    public List<java.util.Map<String,Object>> transactions(Authentication actor,@PathVariable long id){
        Long owner=jdbc.query("SELECT sa_holder_id FROM savings_accounts WHERE sa_account_id=?",rs->rs.next()?rs.getLong(1):null,id);
        if(owner==null||owner!=userId(actor))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Savings account not found");
        return jdbc.queryForList("SELECT st_id,st_reference_id,st_entry_type,st_amount,st_currency_id,st_balance_after,st_remarks,st_posted_at FROM savings_transactions WHERE st_account_id=? ORDER BY st_posted_at,st_id",id);
    }

    @GetMapping("/{id}/statement")
    public StatementPage statement(Authentication actor,@PathVariable long id,@RequestParam(required=false) java.time.LocalDate from,@RequestParam(required=false) java.time.LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){
        long owner=userId(actor);find(id,owner);if(from!=null&&to!=null&&from.isAfter(to))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Statement start date must be on or before end date");int p=Math.max(0,page),s=Math.min(100,Math.max(1,size));String range=" WHERE st_account_id=?"+(from==null?"":" AND st_posted_at>=?")+(to==null?"":" AND st_posted_at<?");java.util.ArrayList<Object> args=new java.util.ArrayList<>();args.add(id);if(from!=null)args.add(java.sql.Timestamp.valueOf(from.atStartOfDay()));if(to!=null)args.add(java.sql.Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM savings_transactions"+range,Long.class,args.toArray());BigDecimal opening=from==null?BigDecimal.ZERO:jdbc.query("SELECT st_balance_after FROM savings_transactions WHERE st_account_id=? AND st_posted_at<? ORDER BY st_posted_at DESC,st_id DESC LIMIT 1",r->r.next()?r.getBigDecimal(1):BigDecimal.ZERO,id,java.sql.Timestamp.valueOf(from.atStartOfDay()));BigDecimal closing=jdbc.query("SELECT st_balance_after FROM savings_transactions"+range+" ORDER BY st_posted_at DESC,st_id DESC LIMIT 1",r->r.next()?r.getBigDecimal(1):opening,args.toArray());
        java.util.ArrayList<Object> pageArgs=new java.util.ArrayList<>(args);pageArgs.add(s);pageArgs.add((long)p*s);List<Map<String,Object>> content=jdbc.queryForList("SELECT st_id id,st_reference_id reference,st_entry_type entryType,st_amount amount,st_currency_id currencyId,st_balance_after balanceAfter,st_remarks remarks,st_posted_at postedAt FROM savings_transactions"+range+" ORDER BY st_posted_at DESC,st_id DESC LIMIT ? OFFSET ?",pageArgs.toArray());return new StatementPage(content,p,s,total,(total+s-1)/s,opening,closing);
    }

    @GetMapping(value="/{id}/statement.csv",produces="text/csv")
    public ResponseEntity<byte[]> statementCsv(Authentication actor,@PathVariable long id,@RequestParam(required=false) java.time.LocalDate from,@RequestParam(required=false) java.time.LocalDate to){
        long owner=userId(actor);find(id,owner);if(from!=null&&to!=null&&from.isAfter(to))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Statement start date must be on or before end date");String range=" WHERE st_account_id=?"+(from==null?"":" AND st_posted_at>=?")+(to==null?"":" AND st_posted_at<?");java.util.ArrayList<Object> args=new java.util.ArrayList<>();args.add(id);if(from!=null)args.add(java.sql.Timestamp.valueOf(from.atStartOfDay()));if(to!=null)args.add(java.sql.Timestamp.valueOf(to.plusDays(1).atStartOfDay()));Integer scale=jdbc.queryForObject("SELECT c.fcr_decimal_places FROM savings_accounts s JOIN fd_currency_ref c ON c.fcr_currency_id=s.sa_currency_id WHERE s.sa_account_id=?",Integer.class,id);StringBuilder csv=new StringBuilder("date,reference,type,amount,balance,currency,remarks\n");for(Map<String,Object> row:jdbc.queryForList("SELECT t.st_posted_at,t.st_reference_id,t.st_entry_type,t.st_amount,t.st_balance_after,c.fcr_iso_code,t.st_remarks FROM savings_transactions t JOIN fd_currency_ref c ON c.fcr_currency_id=t.st_currency_id"+range+" ORDER BY t.st_posted_at DESC,t.st_id DESC",args.toArray())){csv.append(csvCell(row.get("st_posted_at").toString())).append(',').append(csvCell(row.get("st_reference_id"))).append(',').append(csvCell(row.get("st_entry_type"))).append(',').append(((BigDecimal)row.get("st_amount")).setScale(scale,RoundingMode.HALF_UP).toPlainString()).append(',').append(((BigDecimal)row.get("st_balance_after")).setScale(scale,RoundingMode.HALF_UP).toPlainString()).append(',').append(csvCell(row.get("fcr_iso_code"))).append(',').append(csvCell(row.get("st_remarks"))).append('\n');}return ResponseEntity.ok().header("Cache-Control","no-store").header("Content-Disposition","attachment; filename=\"deposit-statement-"+id+".csv\"").body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String csvCell(Object raw){String v=raw==null?"":raw.toString();if(!v.isEmpty()&&"=+-@".indexOf(v.charAt(0))>=0)v="'"+v;return "\""+v.replace("\"","\"\"")+"\"";}

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    public SavingsAccountView openMine(Authentication actor,HttpServletRequest http,@Valid @RequestBody OpenAccountRequest request,
            @RequestHeader(value="Idempotency-Key",required=false) @Size(max=255) String key){
        long ownerId=userId(actor);
        return create(ownerId,request,"CUSTOMER:"+ownerId,operationKey(key),http.getHeader("Authorization"),true);
    }

    @PostMapping("/admin") @ResponseStatus(HttpStatus.CREATED) @Transactional @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public SavingsAccountView openForCustomer(Authentication actor,HttpServletRequest http,@Valid @RequestBody AdminOpenAccountRequest request,
            @RequestHeader(value="Idempotency-Key",required=false) @Size(max=255) String key){
        return create(request.userId,new OpenAccountRequest(request.currencyCode,request.displayName,request.productId),"STAFF:"+actor.getName(),operationKey(key),http.getHeader("Authorization"),false);
    }

    @ExceptionHandler(CustomerReferenceNotReadyException.class)
    public ResponseEntity<Map<String,String>> customerReferenceNotReady(){
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After","1")
                .body(Map.of("code","USER_REFERENCE_NOT_READY","message","Customer reference is synchronizing. Retry shortly."));
    }

    private SavingsAccountView create(long ownerId,OpenAccountRequest request,String actorKey,String key,String authorization,boolean customer){
        String requestHash=hash(ownerId+"|"+request.currencyCode.trim().toUpperCase(java.util.Locale.ROOT)+"|"+request.displayName.trim()+"|"+request.productId);
        try{jdbc.update("INSERT INTO fd_operation_idempotency(foi_operation_scope,foi_actor_key,foi_idempotency_key,foi_request_hash,foi_status) VALUES('SAVINGS_ACCOUNT_OPENING',?,?,?,'PENDING')",actorKey,key,requestHash);}
        catch(DuplicateKeyException e){
            Map<String,Object> existing=jdbc.queryForMap("SELECT foi_request_hash,foi_status,foi_aggregate_id FROM fd_operation_idempotency WHERE foi_operation_scope='SAVINGS_ACCOUNT_OPENING' AND foi_actor_key=? AND foi_idempotency_key=?",actorKey,key);
            if(!requestHash.equals(existing.get("foi_request_hash")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key was already used for a different savings-account request");
            if("COMPLETED".equals(existing.get("foi_status"))&&existing.get("foi_aggregate_id")!=null)return find(((Number)existing.get("foi_aggregate_id")).longValue(),ownerId);
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Savings-account request is already in progress");
        }
        Integer owner=jdbc.query("SELECT 1 FROM fd_user_ref WHERE fur_user_id=? AND fur_user_status='ACTIVE'",rs->rs.next()?1:null,ownerId);
        if(owner==null)throw new CustomerReferenceNotReadyException();
        Long currencyId=jdbc.query("SELECT fcr_currency_id FROM fd_currency_ref WHERE UPPER(fcr_iso_code)=UPPER(?)",rs->rs.next()?rs.getLong(1):null,request.currencyCode.trim());
        if(currencyId==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Currency is not available in the FD service yet");
        JsonNode offer=null;String terms=null;BigDecimal minimum=BigDecimal.ZERO;
        if(request.productId!=null){if(customer)CustomerProductAccess.requireVerified(ownerId);offer=offers.get("SAVINGS",request.productId,authorization);JsonNode configured=null;for(JsonNode c:offer.path("currencies"))if(request.currencyCode.equalsIgnoreCase(c.path("code").asText()))configured=c;if(configured==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Currency is not configured for this product");minimum=offer.path("minimumBalance").decimalValue();java.util.ArrayList<JsonNode> bands=new java.util.ArrayList<>();for(JsonNode b:offer.path("interestBands"))if(request.currencyCode.equalsIgnoreCase(b.path("currencyCode").asText()))bands.add(b.deepCopy());try{terms=json.writeValueAsString(bands);}catch(Exception e){throw new IllegalStateException("Could not freeze account product terms",e);}}
        String accountNumber="SA-"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase();
        if(offer==null)jdbc.update("INSERT INTO savings_accounts(sa_account_number,sa_holder_id,sa_currency_id,sa_display_name,sa_balance,sa_status,sa_created_date,sa_account_type,sa_minimum_balance) VALUES(?,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6),'SAVINGS',0)",accountNumber,ownerId,currencyId,request.displayName.trim(),BigDecimal.ZERO);
        else jdbc.update("INSERT INTO savings_accounts(sa_account_number,sa_holder_id,sa_currency_id,sa_display_name,sa_balance,sa_status,sa_created_date,sa_account_type,sa_product_id,sa_product_code,sa_product_name,sa_minimum_balance,sa_interest_terms,sa_interest_start_date) VALUES(?,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6),'SAVINGS',?,?,?,?,CAST(? AS JSON),?)",accountNumber,ownerId,currencyId,request.displayName.trim(),BigDecimal.ZERO,request.productId,offer.path("productCode").asText(),offer.path("productName").asText(),minimum,terms,clock.businessDate());
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        jdbc.update("UPDATE fd_operation_idempotency SET foi_status='COMPLETED',foi_aggregate_id=?,foi_response_reference=?,foi_completed_at=UTC_TIMESTAMP(6) WHERE foi_operation_scope='SAVINGS_ACCOUNT_OPENING' AND foi_actor_key=? AND foi_idempotency_key=?",id,Long.toString(id),actorKey,key);
        return find(id,ownerId);
    }

    private String operationKey(String key){
        if(key==null)return UUID.randomUUID().toString();
        String normalized=key.trim();
        if(normalized.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency-Key must not be blank");
        return normalized;
    }

    private String selection(){return "SELECT s.sa_account_id,s.sa_account_number,s.sa_display_name,s.sa_currency_id,c.fcr_iso_code,s.sa_balance,s.sa_status,s.sa_created_date,s.sa_account_type,s.sa_product_id,s.sa_product_code,s.sa_product_name,s.sa_minimum_balance FROM savings_accounts s JOIN fd_currency_ref c ON c.fcr_currency_id=s.sa_currency_id ";}
    private SavingsAccountView find(long id,long owner){return jdbc.query(selection()+"WHERE s.sa_account_id=? AND s.sa_holder_id=?",rs->{if(!rs.next())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Savings account not found");return view(rs);},id,owner);}
    private SavingsAccountView findAny(long id){return jdbc.query(selection()+"WHERE s.sa_account_id=?",rs->{if(!rs.next())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Savings account not found");return view(rs);},id);}
    private SavingsAccountView view(java.sql.ResultSet rs)throws java.sql.SQLException{BigDecimal balance=rs.getBigDecimal(6),min=rs.getBigDecimal(13);return new SavingsAccountView(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getString(5),balance,rs.getString(7),rs.getTimestamp(8).toInstant().toString(),rs.getString(9),(Long)rs.getObject(10),rs.getString(11),rs.getString(12),min,balance.subtract(min).max(BigDecimal.ZERO));}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}

    private long userId(Authentication actor){
        Object value=actor.getDetails() instanceof java.util.Map<?,?> details?details.get("userId"):null;
        if(!(value instanceof Number number)||number.longValue()<=0)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Token does not contain a valid customer identity");
        return number.longValue();
    }

    public record OpenAccountRequest(@NotBlank @Size(max=10) String currencyCode,@NotBlank @Size(max=80) String displayName,Long productId){public OpenAccountRequest(String currencyCode,String displayName){this(currencyCode,displayName,null);}}
    public record SavingsCurrencyView(String currencyCode,String currencyName,int decimalPlaces){}
    public record AdminOpenAccountRequest(long userId,@NotBlank @Size(max=10) String currencyCode,@NotBlank @Size(max=80) String displayName,Long productId){}
    public record DemoOptions(boolean enabled){}
    public record DemoOpeningRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 8) BigDecimal amount){}
    public record SavingsAccountView(@com.fasterxml.jackson.annotation.JsonProperty("account_id") long accountId,@com.fasterxml.jackson.annotation.JsonProperty("account_number") String accountNumber,@com.fasterxml.jackson.annotation.JsonProperty("display_name") String displayName,long currencyId,@com.fasterxml.jackson.annotation.JsonProperty("currency_code") String currencyCode,BigDecimal balance,String status,@com.fasterxml.jackson.annotation.JsonProperty("created_date") String createdDate,String accountType,Long productId,String productCode,String productName,BigDecimal minimumBalance,BigDecimal availableBalance){}
    public record StatementPage(List<Map<String,Object>> content,int page,int size,long totalElements,long totalPages,BigDecimal openingBalance,BigDecimal closingBalance){}
    private static final class CustomerReferenceNotReadyException extends RuntimeException {}
}
