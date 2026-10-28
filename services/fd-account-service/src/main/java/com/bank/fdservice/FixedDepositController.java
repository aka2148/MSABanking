package com.bank.fdservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fixed-deposits")
public class FixedDepositController {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final DemoClockService clock; private final MpinProofVerifier mpin; private final PostingService postings;
    public FixedDepositController(JdbcTemplate jdbc,ObjectMapper json,DemoClockService clock,MpinProofVerifier mpin,PostingService postings){this.jdbc=jdbc;this.json=json;this.clock=clock;this.mpin=mpin;this.postings=postings;}

    @GetMapping
    public List<FdView> mine(Authentication actor){return jdbc.query("SELECT a.fa_account_id,a.fa_account_number,a.fa_principal_amount,a.fa_currency_id,c.fcr_iso_code,a.fa_tenure_months,a.fa_interest_rate,a.fa_open_date,a.fa_maturity_date,a.fa_maturity_amount,a.fa_status,a.fa_funding_savings_account_id,a.fa_product_id,p.fpr_product_code,p.fpr_product_name,a.fa_compounding_frequency,a.fa_payout_frequency,a.fa_policy_version FROM fd_accounts a JOIN fd_currency_ref c ON c.fcr_currency_id=a.fa_currency_id JOIN fd_product_ref p ON p.fpr_product_id=a.fa_product_id WHERE a.fa_primary_holder_id=? ORDER BY a.fa_account_id",(r,n)->view(r),userId(actor));}

    @GetMapping("/{id}")
    public FdView getMine(Authentication actor,@PathVariable long id){return find(id,userId(actor));}

    @PostMapping("/estimate")
    public Estimate estimate(@Valid @RequestBody EstimateRequest request){
        var q=new FdQuoteService(jdbc,clock).resolve(new BookingRequest(request.productId(),null,request.principal(),request.currencyCode(),request.tenureMonths()),null);
        return new Estimate(request.productId(),request.currencyCode().toUpperCase(java.util.Locale.ROOT),q.scale(),q.principal(),q.rate(),q.compounding(),q.payout(),q.open(),q.maturity(),q.estimate().subtract(q.principal()),q.estimate());
    }
    public record EstimateRequest(@NotNull @Min(1) Long productId,@NotBlank @Size(max=10) String currencyCode,@NotNull @DecimalMin("0.01") BigDecimal principal,@Min(1) int tenureMonths){}
    public record Estimate(long productId,String currencyCode,int currencyScale,BigDecimal principal,BigDecimal annualRate,String compoundingBasis,String payoutFrequency,LocalDate openDate,LocalDate maturityDate,BigDecimal estimatedInterest,BigDecimal estimatedMaturityAmount){}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    public FdView book(Authentication actor,@RequestHeader("Idempotency-Key") @NotBlank @Size(max=255) String key,@Valid @RequestBody BookingRequest request){
        long userId=userId(actor);CustomerProductAccess.requireVerified(userId);String hash=hash(request.productId()+"|"+request.savingsAccountId()+"|"+request.principal()+"|"+request.currencyCode()+"|"+request.tenureMonths);
        Long replay=claim("FD_BOOKING",userId,key,hash);if(replay!=null)return find(replay,userId);
        FdQuoteService.Quote quote=new FdQuoteService(jdbc,clock).resolve(request,userId);
        Map<String,Object> product=quote.product();long currencyId=quote.currencyId();int scale=quote.scale();
        BigDecimal principal=quote.principal(),rate=quote.rate(),estimate=quote.estimate();
        String compounding=quote.compounding(),payout=quote.payout(),penaltyStr=quote.penaltyStr(),policy=quote.policy();
        LocalDate open=quote.open(),maturity=quote.maturity();
        Map<String,Object> termsMap=mapOf("productId",request.productId(),"productCode",product.get("code"),"productName",product.get("name"),"currencyCode",request.currencyCode(),"currencyScale",scale,"principal",principal,"annualRate",rate,"tenureMonths",request.tenureMonths(),"compoundingBasis",compounding,"payoutFrequency",payout,"prematurePenaltyPoints",penaltyStr==null||penaltyStr.isBlank()?null:penaltyStr,"policyVersion",policy,"bookingDate",open.toString(),"maturityDate",maturity.toString(),"calculationConvention","ACTUAL_365");
        if(request.expectedAnnualRate()!=null){
            termsMap.put("disclosedAnnualRate",request.expectedAnnualRate());
            termsMap.put("disclosureVerified",true);
        }
        String terms=json(termsMap);
        String accountNumber="FD-"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase();
        jdbc.update("INSERT INTO fd_accounts(fa_account_number,fa_primary_holder_id,fa_product_id,fa_principal_amount,fa_currency_id,fa_tenure_months,fa_interest_rate,fa_compounding_frequency,fa_payout_frequency,fa_open_date,fa_maturity_date,fa_maturity_amount,fa_status,fa_funding_savings_account_id,fa_terms_snapshot,fa_policy_version) VALUES(?,?,?,?,?,?,?,?,?, ?,?,?,'PENDING',?,CAST(? AS JSON),?)",accountNumber,userId,request.productId(),principal.setScale(scale,RoundingMode.HALF_UP),currencyId,request.tenureMonths(),rate,compounding,payout,open.atStartOfDay(),java.sql.Date.valueOf(maturity),estimate.setScale(scale,RoundingMode.HALF_UP),request.savingsAccountId(),terms,policy);
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        jdbc.update("INSERT INTO fd_account_events(fae_account_id,fae_event_type,fae_payload,fae_aggregate_version) VALUES(?,'BOOKED',CAST(? AS JSON),1)",id,"{\"status\":\"PENDING\"}");
        complete("FD_BOOKING",userId,key,id);
        return find(id,userId);
    }

    @PostMapping("/{id}/fund") @Transactional
    public FdView fund(Authentication actor,@PathVariable long id,@RequestHeader("Idempotency-Key") @NotBlank @Size(max=255) String key,@RequestHeader(value="X-MPIN-Proof",required=false) String proof,@RequestBody(required=false) FundingChoice choice){
        long userId=userId(actor);CustomerProductAccess.requireVerified(userId);String hash=hash(Long.toString(id));Long replay=claim("FD_FUNDING",userId,key,hash);if(replay!=null)return find(replay,userId);
        Map<String,Object> fd=jdbc.query("SELECT fa_primary_holder_id,fa_principal_amount,fa_currency_id,fa_status,fa_funding_savings_account_id FROM fd_accounts WHERE fa_account_id=? FOR UPDATE",r->r.next()?Map.of("owner",r.getLong(1),"principal",r.getBigDecimal(2),"currency",r.getLong(3),"status",r.getString(4),"savings",r.getLong(5)):null,id);
        if(fd==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Fixed deposit not found");
        if(((Number)fd.get("owner")).longValue()!=userId)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Fixed deposit not found");
        if(!"PENDING".equals(fd.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only a pending fixed deposit can be funded");
        long savingsId=((Number)fd.get("savings")).longValue(),currency=((Number)fd.get("currency")).longValue();BigDecimal principal=(BigDecimal)fd.get("principal");
        if(choice!=null && choice.savingsAccountId()!=null && choice.savingsAccountId()!=savingsId)throw new ResponseStatusException(HttpStatus.CONFLICT,"Funding account differs from the account selected at booking");
        String jti=mpin.verify(actor,proof,"FD_FUNDING",userId,MpinProofVerifier.canonical("FD_FUNDING",id,principal));
        Map<String,Object> savings=jdbc.query("SELECT sa_balance,sa_status,sa_minimum_balance FROM savings_accounts WHERE sa_account_id=? AND sa_holder_id=? AND sa_currency_id=? FOR UPDATE",r->r.next()?Map.of("balance",r.getBigDecimal(1),"status",r.getString(2),"minimum",r.getBigDecimal(3)):null,savingsId,userId,currency);
        if(savings==null||!"ACTIVE".equals(savings.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Funding savings account is unavailable or mismatched");
        mpin.consume(jti,userId,"FD_FUNDING");
        BigDecimal balance=(BigDecimal)savings.get("balance");if(balance.compareTo(principal)<0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Insufficient savings balance");
        BigDecimal afterPreview=balance.subtract(principal);if(afterPreview.compareTo((BigDecimal)savings.get("minimum"))<0)throw new BelowMinimumBalanceException(((BigDecimal)savings.get("minimum")).toPlainString()+" "+jdbc.queryForObject("SELECT fcr_iso_code FROM fd_currency_ref WHERE fcr_currency_id=?",String.class,currency));
        int scale=currencyScale(currency);String ref=UUID.randomUUID().toString();
        PostingService.MovementResult movement=postings.postSavingsMovement(id,savingsId,currency,principal,"DEBIT",ref,"FD funding #"+id,actor.getName(),"FUNDING:"+id,"FUNDING","SAVINGS","FIXED_DEPOSIT");
        BigDecimal after=movement.balanceAfter();
        long txnType=jdbc.queryForObject("SELECT tt_txn_type_id FROM transaction_types WHERE tt_txn_type_code='FD_FUNDING'",Long.class);
        jdbc.update("INSERT INTO fd_transactions(ft_account_id,ft_txn_type_id,ft_amount,ft_balance_after,ft_currency_id,ft_remarks,ft_created_by,ft_source_savings_account_id) VALUES(?,?,?,?,?,?,?,?)",id,txnType,principal,null,currency,"Funded from selected savings account",actor.getName(),savingsId);
        jdbc.update("UPDATE fd_accounts SET fa_status='ACTIVE',fa_updated_by=?,fa_updated_date=UTC_TIMESTAMP(6),fa_row_version=fa_row_version+1 WHERE fa_account_id=? AND fa_status='PENDING'",actor.getName(),id);
        jdbc.update("INSERT INTO fd_account_events(fae_account_id,fae_event_type,fae_payload,fae_aggregate_version) VALUES(?,'FUNDED',CAST(? AS JSON),2)",id,"{\"status\":\"ACTIVE\"}");
        complete("FD_FUNDING",userId,key,id);return find(id,userId);
    }

    private Long claim(String scope,long actor,String key,String hash){
        try{jdbc.update("INSERT INTO fd_operation_idempotency(foi_operation_scope,foi_actor_key,foi_idempotency_key,foi_request_hash,foi_status) VALUES(?,?,?,?,'PENDING')",scope,Long.toString(actor),key,hash);return null;}
        catch(DuplicateKeyException e){Map<String,Object> row=jdbc.queryForMap("SELECT foi_request_hash,foi_status,foi_aggregate_id FROM fd_operation_idempotency WHERE foi_operation_scope=? AND foi_actor_key=? AND foi_idempotency_key=?",scope,Long.toString(actor),key);if(!hash.equals(row.get("foi_request_hash")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key was already used for a different request");if("COMPLETED".equals(row.get("foi_status"))&&row.get("foi_aggregate_id")!=null)return ((Number)row.get("foi_aggregate_id")).longValue();throw new ResponseStatusException(HttpStatus.CONFLICT,"Matching operation is already in progress");}
    }
    private void complete(String scope,long actor,String key,long id){jdbc.update("UPDATE fd_operation_idempotency SET foi_status='COMPLETED',foi_aggregate_id=?,foi_response_reference=?,foi_completed_at=UTC_TIMESTAMP(6) WHERE foi_operation_scope=? AND foi_actor_key=? AND foi_idempotency_key=?",id,Long.toString(id),scope,Long.toString(actor),key);}
    private FdView find(long id,long user){return jdbc.query("SELECT a.fa_account_id,a.fa_account_number,a.fa_principal_amount,a.fa_currency_id,c.fcr_iso_code,a.fa_tenure_months,a.fa_interest_rate,a.fa_open_date,a.fa_maturity_date,a.fa_maturity_amount,a.fa_status,a.fa_funding_savings_account_id,a.fa_product_id,p.fpr_product_code,p.fpr_product_name,a.fa_compounding_frequency,a.fa_payout_frequency,a.fa_policy_version FROM fd_accounts a JOIN fd_currency_ref c ON c.fcr_currency_id=a.fa_currency_id JOIN fd_product_ref p ON p.fpr_product_id=a.fa_product_id WHERE a.fa_account_id=? AND a.fa_primary_holder_id=?",r->{if(!r.next())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Fixed deposit not found");return view(r);},id,user);}
    private FdView view(java.sql.ResultSet r)throws java.sql.SQLException{
        long currencyId=r.getLong(4);
        BigDecimal principal=r.getBigDecimal(3),rate=r.getBigDecimal(7);
        java.time.LocalDate openDate=r.getObject(8,java.time.LocalDateTime.class).toLocalDate();
        java.time.LocalDate maturityDate=r.getDate(9).toLocalDate();
        String compounding=r.getString(16);
        BigDecimal finalMaturityAmount=FdCompounding.maturity(principal,rate,openDate,maturityDate,currencyScale(currencyId),compounding);
        return new FdView(r.getLong(1),r.getString(2),principal,currencyId,r.getString(5),r.getInt(6),rate,
                openDate,maturityDate,finalMaturityAmount,r.getString(11),r.getLong(12),r.getLong(13),
                r.getString(14),r.getString(15),compounding,r.getString(17),r.getString(18));
    }
    private long userId(Authentication actor){Object value=actor.getDetails() instanceof Map<?,?> d?d.get("userId"):null;if(!(value instanceof Number n)||n.longValue()<=0)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Token does not contain a valid customer identity");return n.longValue();}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String json(Object value){try{return this.json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Could not serialize FD terms",e);}}
    private Map<String,Object> mapOf(Object... values){var m=new java.util.LinkedHashMap<String,Object>();for(int i=0;i<values.length;i+=2)m.put((String)values[i],values[i+1]);return m;}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private int currencyScale(long id){Integer n=jdbc.queryForObject("SELECT fcr_decimal_places FROM fd_currency_ref WHERE fcr_currency_id=?",Integer.class,id);return n==null?2:n;}

    public record BookingRequest(@NotNull @Min(1) Long productId,@NotNull @Min(1) Long savingsAccountId,@NotNull @DecimalMin("0.01") BigDecimal principal,@NotBlank @Size(max=10) String currencyCode,@Min(1) int tenureMonths,@com.fasterxml.jackson.annotation.JsonProperty("expected_annual_rate") BigDecimal expectedAnnualRate){
        public BookingRequest(Long productId, Long savingsAccountId, BigDecimal principal, String currencyCode, int tenureMonths){
            this(productId, savingsAccountId, principal, currencyCode, tenureMonths, null);
        }
    }
    public record FundingChoice(@NotNull @Min(1) Long savingsAccountId){}
    public record FdView(@com.fasterxml.jackson.annotation.JsonProperty("account_id") long accountId,@com.fasterxml.jackson.annotation.JsonProperty("account_number") String accountNumber,@com.fasterxml.jackson.annotation.JsonProperty("principal_amount") BigDecimal principal,@com.fasterxml.jackson.annotation.JsonProperty("currency_id") long currencyId,@com.fasterxml.jackson.annotation.JsonProperty("currency_code") String currencyCode,@com.fasterxml.jackson.annotation.JsonProperty("tenure_months") int tenureMonths,@com.fasterxml.jackson.annotation.JsonProperty("annual_rate") BigDecimal annualRate,@com.fasterxml.jackson.annotation.JsonProperty("open_date") LocalDate openDate,@com.fasterxml.jackson.annotation.JsonProperty("maturity_date") LocalDate maturityDate,@com.fasterxml.jackson.annotation.JsonProperty("maturity_amount") BigDecimal estimatedMaturityAmount,String status,@com.fasterxml.jackson.annotation.JsonProperty("funding_savings_account_id") long fundingSavingsAccountId,@com.fasterxml.jackson.annotation.JsonProperty("product_id") long productId,@com.fasterxml.jackson.annotation.JsonProperty("product_code") String productCode,@com.fasterxml.jackson.annotation.JsonProperty("product_name") String productName,@com.fasterxml.jackson.annotation.JsonProperty("compounding_frequency") String compoundingFrequency,@com.fasterxml.jackson.annotation.JsonProperty("payout_frequency") String payoutFrequency,@com.fasterxml.jackson.annotation.JsonProperty("policy_version") String policyVersion,@com.fasterxml.jackson.annotation.JsonProperty("funding_recorded") boolean fundingRecorded){
        public FdView(long accountId,String accountNumber,BigDecimal principal,long currencyId,String currencyCode,int tenureMonths,BigDecimal annualRate,LocalDate openDate,LocalDate maturityDate,BigDecimal estimatedMaturityAmount,String status,long fundingSavingsAccountId,long productId,String productCode,String productName,String compoundingFrequency,String payoutFrequency,String policyVersion){this(accountId,accountNumber,principal,currencyId,currencyCode,tenureMonths,annualRate,openDate,maturityDate,estimatedMaturityAmount,status,fundingSavingsAccountId,productId,productCode,productName,compoundingFrequency,payoutFrequency,policyVersion,!"PENDING".equals(status));}
    }
}
