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
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final DemoClockService clock; private final MpinProofVerifier mpin;
    public FixedDepositController(JdbcTemplate jdbc,ObjectMapper json,DemoClockService clock,MpinProofVerifier mpin){this.jdbc=jdbc;this.json=json;this.clock=clock;this.mpin=mpin;}

    @GetMapping
    public List<FdView> mine(Authentication actor){return jdbc.query("SELECT a.fa_account_id,a.fa_account_number,a.fa_principal_amount,a.fa_currency_id,c.fcr_iso_code,a.fa_tenure_months,a.fa_interest_rate,a.fa_open_date,a.fa_maturity_date,a.fa_maturity_amount,a.fa_status,a.fa_funding_savings_account_id,a.fa_product_id,p.fpr_product_code,p.fpr_product_name,a.fa_compounding_frequency,a.fa_payout_frequency,a.fa_policy_version FROM fd_accounts a JOIN fd_currency_ref c ON c.fcr_currency_id=a.fa_currency_id JOIN fd_product_ref p ON p.fpr_product_id=a.fa_product_id WHERE a.fa_primary_holder_id=? ORDER BY a.fa_account_id",(r,n)->view(r),userId(actor));}

    @GetMapping("/{id}")
    public FdView getMine(Authentication actor,@PathVariable long id){return find(id,userId(actor));}

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    public FdView book(Authentication actor,@RequestHeader("Idempotency-Key") @NotBlank @Size(max=255) String key,@Valid @RequestBody BookingRequest request){
        long userId=userId(actor);CustomerProductAccess.requireVerified(userId);String hash=hash(request.productId+"|"+request.savingsAccountId+"|"+request.principal+"|"+request.currencyCode+"|"+request.tenureMonths);
        Long replay=claim("FD_BOOKING",userId,key,hash);if(replay!=null)return find(replay,userId);
        Map<String,Object> product=jdbc.query("SELECT fpr_product_code,fpr_product_name,fpr_status FROM fd_product_ref WHERE fpr_product_id=?",r->r.next()?Map.of("code",r.getString(1),"name",r.getString(2),"status",r.getString(3)):null,request.productId);
        if(product==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Fixed deposit product not found");
        if(!"ACTIVE".equalsIgnoreCase((String)product.get("status")))throw bad("Fixed deposit product is not active");
        Map<String,Object> currency=jdbc.query("SELECT fcr_currency_id,fcr_decimal_places FROM fd_currency_ref WHERE UPPER(fcr_iso_code)=UPPER(?)",r->r.next()?Map.of("id",r.getLong(1),"scale",r.getInt(2)):null,request.currencyCode);
        if(currency==null)throw bad("Currency reference has not arrived from the Product service");
        long currencyId=((Number)currency.get("id")).longValue();int scale=((Number)currency.get("scale")).intValue();
        if(scale<0||scale>8)throw bad("Currency decimal scale is invalid");
        if(request.principal.compareTo(BigDecimal.ZERO)<=0)throw bad("Principal amount must be positive");
        BigDecimal principal;try{principal=request.principal.setScale(scale,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw bad("Principal exceeds the currency decimal scale");}
        if(request.tenureMonths<1)throw bad("Tenure months must be at least 1");
        Map<String,Object> ownerAccount=jdbc.query("SELECT sa_account_id FROM savings_accounts WHERE sa_account_id=? AND sa_holder_id=? AND sa_currency_id=? AND sa_status='ACTIVE'",r->r.next()?Map.of("id",r.getLong(1)):null,request.savingsAccountId,userId,currencyId);
        if(ownerAccount==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Eligible owned savings account not found");
        Map<String,Object> pcc=jdbc.query("SELECT minimum_amount,maximum_amount,premature_penalty_points FROM fd_product_currency_ref WHERE product_id=? AND currency_id=?",r->r.next()?Map.of("min",r.getBigDecimal(1),"max",r.getBigDecimal(2),"penalty",r.getBigDecimal(3)==null?"":r.getBigDecimal(3).toPlainString()):null,request.productId,currencyId);
        if(pcc==null)throw bad("Currency "+request.currencyCode+" is not supported for this product");
        BigDecimal minAmount=(BigDecimal)pcc.get("min");BigDecimal maxAmount=(BigDecimal)pcc.get("max");
        if(principal.compareTo(minAmount)<0||principal.compareTo(maxAmount)>0)throw bad("Principal amount "+principal+" is outside permitted bounds ["+minAmount+", "+maxAmount+"]");
        LocalDate open=clock.businessDate();
        List<Map<String,Object>> candidateBands=jdbc.queryForList("SELECT annual_rate,compounding_basis,payout_frequency,effective_from,effective_to FROM fd_product_rate_ref WHERE product_id=? AND currency_id=? AND minimum_tenure_months<=? AND maximum_tenure_months>=?",request.productId,currencyId,request.tenureMonths,request.tenureMonths);
        if(candidateBands.isEmpty())throw bad("Requested tenure of "+request.tenureMonths+" months does not match any configured tenure rate band");
        List<Map<String,Object>> effectiveBands=candidateBands.stream().filter(b->{
            LocalDate from=b.get("effective_from")==null?null:(b.get("effective_from") instanceof java.sql.Date d?d.toLocalDate():LocalDate.parse(b.get("effective_from").toString().substring(0,10)));
            LocalDate to=b.get("effective_to")==null?null:(b.get("effective_to") instanceof java.sql.Date d?d.toLocalDate():LocalDate.parse(b.get("effective_to").toString().substring(0,10)));
            return (from==null||!open.isBefore(from))&&(to==null||!open.isAfter(to));
        }).toList();
        if(effectiveBands.isEmpty()){
            boolean allFuture=candidateBands.stream().allMatch(b->{
                LocalDate from=b.get("effective_from")==null?null:(b.get("effective_from") instanceof java.sql.Date d?d.toLocalDate():LocalDate.parse(b.get("effective_from").toString().substring(0,10)));
                return from!=null&&open.isBefore(from);
            });
            if(allFuture){
                LocalDate earliest=candidateBands.stream().map(b->b.get("effective_from") instanceof java.sql.Date d?d.toLocalDate():LocalDate.parse(b.get("effective_from").toString().substring(0,10))).min(LocalDate::compareTo).orElse(null);
                throw bad("NOT_YET_EFFECTIVE: The rate schedule is not effective until "+earliest);
            }
            boolean allPast=candidateBands.stream().allMatch(b->{
                LocalDate to=b.get("effective_to")==null?null:(b.get("effective_to") instanceof java.sql.Date d?d.toLocalDate():LocalDate.parse(b.get("effective_to").toString().substring(0,10)));
                return to!=null&&open.isAfter(to);
            });
            if(allPast){
                LocalDate latest=candidateBands.stream().map(b->b.get("effective_to") instanceof java.sql.Date d?d.toLocalDate():LocalDate.parse(b.get("effective_to").toString().substring(0,10))).max(LocalDate::compareTo).orElse(null);
                throw bad("EXPIRED: The rate schedule expired on "+latest);
            }
            throw bad("NO_APPLICABLE_RATE: No active rate schedule is effective on "+open);
        }
        BigDecimal rate;
        String compounding;
        String payout;
        if(effectiveBands.size()>1){
            BigDecimal firstRate=(BigDecimal)effectiveBands.get(0).get("annual_rate");
            boolean allSame=effectiveBands.stream().allMatch(b->((BigDecimal)b.get("annual_rate")).compareTo(firstRate)==0);
            if(!allSame)throw new ResponseStatusException(HttpStatus.CONFLICT,"AMBIGUOUS_CONFIGURATION: Multiple rate schedules overlap on "+open+" with differing rates");
            rate=firstRate;compounding=(String)effectiveBands.get(0).get("compounding_basis");payout=(String)effectiveBands.get(0).get("payout_frequency");
        } else {
            rate=(BigDecimal)effectiveBands.get(0).get("annual_rate");compounding=(String)effectiveBands.get(0).get("compounding_basis");payout=(String)effectiveBands.get(0).get("payout_frequency");
        }
        if(request.expectedAnnualRate!=null && rate.compareTo(request.expectedAnnualRate)!=0){
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Disclosed interest rate ("+request.expectedAnnualRate+") differs from authoritative rate ("+rate+") on "+open+". Please review the updated disclosure before booking.");
        }
        String penaltyStr=(String)pcc.get("penalty");
        LocalDate maturity=open.plusMonths(request.tenureMonths);BigDecimal estimate=FdCompounding.maturity(principal,rate,open,maturity,scale,compounding);
        String policy="academic-v2-"+FdCompounding.basis(compounding).toLowerCase(java.util.Locale.ROOT);
        Map<String,Object> termsMap=mapOf("productId",request.productId,"productCode",product.get("code"),"productName",product.get("name"),"currencyCode",request.currencyCode,"currencyScale",scale,"principal",principal,"annualRate",rate,"tenureMonths",request.tenureMonths,"compoundingBasis",compounding,"payoutFrequency",payout,"prematurePenaltyPoints",penaltyStr==null||penaltyStr.isBlank()?null:penaltyStr,"policyVersion",policy,"bookingDate",open.toString(),"maturityDate",maturity.toString(),"calculationConvention","ACTUAL_365");
        if(request.expectedAnnualRate!=null){
            termsMap.put("disclosedAnnualRate",request.expectedAnnualRate);
            termsMap.put("disclosureVerified",true);
        }
        String terms=json(termsMap);
        String accountNumber="FD-"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase();
        jdbc.update("INSERT INTO fd_accounts(fa_account_number,fa_primary_holder_id,fa_product_id,fa_principal_amount,fa_currency_id,fa_tenure_months,fa_interest_rate,fa_compounding_frequency,fa_payout_frequency,fa_open_date,fa_maturity_date,fa_maturity_amount,fa_status,fa_funding_savings_account_id,fa_terms_snapshot,fa_policy_version) VALUES(?,?,?,?,?,?,?,?,?, ?,?,?,'PENDING',?,CAST(? AS JSON),?)",accountNumber,userId,request.productId,principal.setScale(scale,RoundingMode.HALF_UP),currencyId,request.tenureMonths,rate,compounding,payout,open.atStartOfDay(),java.sql.Date.valueOf(maturity),estimate.setScale(scale,RoundingMode.HALF_UP),request.savingsAccountId,terms,policy);
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
        Map<String,Object> savings=jdbc.query("SELECT sa_balance,sa_status FROM savings_accounts WHERE sa_account_id=? AND sa_holder_id=? AND sa_currency_id=? FOR UPDATE",r->r.next()?Map.of("balance",r.getBigDecimal(1),"status",r.getString(2)):null,savingsId,userId,currency);
        if(savings==null||!"ACTIVE".equals(savings.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Funding savings account is unavailable or mismatched");
        mpin.consume(jti,userId,"FD_FUNDING");
        BigDecimal balance=(BigDecimal)savings.get("balance");if(balance.compareTo(principal)<0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Insufficient savings balance");
        int scale=currencyScale(currency);BigDecimal after=balance.subtract(principal).setScale(scale,RoundingMode.HALF_UP);String ref=UUID.randomUUID().toString();
        jdbc.update("UPDATE savings_accounts SET sa_balance=?,sa_row_version=sa_row_version+1 WHERE sa_account_id=?",after,savingsId);
        jdbc.update("INSERT INTO savings_transactions(st_reference_id,st_account_id,st_entry_type,st_amount,st_currency_id,st_balance_after,st_remarks,st_posted_at) VALUES(?,?,'DEBIT',?,?,?,?,UTC_TIMESTAMP(6))",ref,savingsId,principal,currency,after,"FD funding #"+id);
        long txnType=jdbc.queryForObject("SELECT tt_txn_type_id FROM transaction_types WHERE tt_txn_type_code='FD_FUNDING'",Long.class);
        jdbc.update("INSERT INTO fd_transactions(ft_account_id,ft_txn_type_id,ft_amount,ft_balance_after,ft_currency_id,ft_remarks,ft_created_by,ft_source_savings_account_id) VALUES(?,?,?,?,?,?,?,?)",id,txnType,principal,null,currency,"Funded from selected savings account",actor.getName(),savingsId);
        jdbc.update("INSERT INTO fd_journals(fj_key,fj_fd_account_id,fj_savings_account_id,fj_currency_id,fj_operation,fj_posted_at,fj_total_debit,fj_total_credit,fj_savings_balance_after) VALUES(?,?,?,?,'FUNDING',UTC_TIMESTAMP(6),?,?,?)","FUNDING:"+id,id,savingsId,currency,principal,principal,after);
        long journalId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        jdbc.update("INSERT INTO fd_journal_lines(jl_journal_id,jl_sequence,jl_account_code,jl_debit,jl_credit) VALUES(?,1,'SAVINGS',?,0),(?,2,'FIXED_DEPOSIT',0,?)",journalId,principal,journalId,principal);
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
    private FdView view(java.sql.ResultSet r)throws java.sql.SQLException{return new FdView(r.getLong(1),r.getString(2),r.getBigDecimal(3),r.getLong(4),r.getString(5),r.getInt(6),r.getBigDecimal(7),r.getObject(8,java.time.LocalDateTime.class).toLocalDate(),r.getDate(9).toLocalDate(),r.getBigDecimal(10),r.getString(11),r.getLong(12),r.getLong(13),r.getString(14),r.getString(15),r.getString(16),r.getString(17),r.getString(18));}
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
