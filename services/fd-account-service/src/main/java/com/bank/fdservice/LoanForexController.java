package com.bank.fdservice;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loan decisions and indicative forex requests live beside the savings ledger they affect. */
@RestController
public class LoanForexController {
    private final JdbcTemplate jdbc;
    private final RestClient products;
    private final MpinProofVerifier mpin;

    public LoanForexController(JdbcTemplate jdbc, @Value("${app.product.base-url:http://localhost:8082}") String productUrl,MpinProofVerifier mpin) {
        this.jdbc = jdbc;
        this.products = RestClient.create(productUrl);
        this.mpin=mpin;
    }

    @GetMapping("/api/v1/loans")
    public List<Map<String,Object>> mine(Authentication actor) {
        return jdbc.queryForList(loanSelect() + " WHERE a.la_customer_id=? ORDER BY a.la_id DESC", userId(actor));
    }

    @GetMapping("/api/v1/loans/{id}")
    public Map<String,Object> mineOne(Authentication actor, @PathVariable long id) { return loan(id, userId(actor)); }

    @GetMapping("/api/v1/loans/{id}/history")
    public List<Map<String,Object>> history(Authentication actor, @PathVariable long id) {
        loan(id,userId(actor));
        return events(id);
    }

    @PostMapping("/api/v1/loans") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> apply(Authentication actor, HttpServletRequest http,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max=255) String key,
            @RequestHeader(value="X-MPIN-Proof",required=false) String proof,
            @Valid @RequestBody LoanRequest request) {
        long customer = userId(actor);
        CustomerProductAccess.requireVerified(jdbc,customer);
        Map<String,Object> replay = jdbc.query("SELECT a.la_id,a.la_product_id,a.la_requested_amount,c.fcr_iso_code,a.la_tenure_months,a.la_purpose,a.la_destination_savings_id FROM loan_applications a JOIN fd_currency_ref c ON c.fcr_currency_id=a.la_currency_id WHERE a.la_customer_id=? AND a.la_idempotency_key=?",
                rs -> rs.next() ? Map.<String,Object>of("id",rs.getLong(1),"product",rs.getLong(2),"amount",rs.getBigDecimal(3),"currency",rs.getString(4),"tenure",rs.getInt(5),"purpose",rs.getString(6),"destination",rs.getLong(7)) : null,customer,key);
        if (replay != null) {
            if (((Number)replay.get("product")).longValue()!=request.productId()
                    || ((BigDecimal)replay.get("amount")).compareTo(request.amount())!=0
                    || !replay.get("currency").equals(request.currencyCode().toUpperCase(java.util.Locale.ROOT))
                    || ((Number)replay.get("tenure")).intValue()!=request.tenureMonths()
                    || !replay.get("purpose").equals(request.purpose().trim())
                    || ((Number)replay.get("destination")).longValue()!=request.destinationSavingsAccountId())
                throw conflict("Idempotency key already belongs to another loan application");
            return loan(((Number)replay.get("id")).longValue(),customer);
        }
        String proofJti=mpin.verify(actor,proof,"LOAN_APPLICATION",customer,MpinProofVerifier.canonical("LOAN_APPLICATION",request.productId(),request.amount(),request.currencyCode(),request.tenureMonths(),request.destinationSavingsAccountId()));
        requireCustomerActive(customer);
        JsonNode offer = offer("LOAN",request.productId(),http);
        Currency currency = currency(offer,request.currencyCode());
        BigDecimal amount = amount(request.amount(),currency);
        BigDecimal rate = rate(offer,request.currencyCode(),BigDecimal.valueOf(request.tenureMonths()));
        requireSavings(request.destinationSavingsAccountId(),customer,currency.id(),false);
        mpin.consume(proofJti,customer,"LOAN_APPLICATION");
        try {
            jdbc.update("INSERT INTO loan_applications(la_customer_id,la_product_id,la_product_name,la_requested_amount,la_currency_id,la_tenure_months,la_annual_rate,la_purpose,la_destination_savings_id,la_idempotency_key) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    customer,request.productId(),offer.path("productName").asText(),amount,currency.id(),request.tenureMonths(),rate,request.purpose().trim(),request.destinationSavingsAccountId(),key);
        } catch (DuplicateKeyException ex) { throw conflict("Loan submission is already being processed"); }
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        event(id,"PENDING_REVIEW",customer,null);
        return loan(id,customer);
    }

    @GetMapping("/api/v1/admin/loans") @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String,Object>> adminList(@RequestParam(defaultValue="PENDING_REVIEW") String status) {
        if (!List.of("PENDING_REVIEW","APPROVED","REJECTED","DISBURSED").contains(status)) throw bad("Unsupported status");
        return jdbc.queryForList(loanSelect() + " WHERE a.la_status=? ORDER BY a.la_id",status);
    }

    @GetMapping("/api/v1/admin/loans/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> adminOne(@PathVariable long id) { return loan(id,null); }

    @GetMapping("/api/v1/admin/loans/{id}/history") @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String,Object>> adminHistory(@PathVariable long id) { loan(id,null); return events(id); }

    @PostMapping("/api/v1/admin/loans/{id}/approve") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Map<String,Object> approve(Authentication actor, HttpServletRequest http, @PathVariable long id) {
        Map<String,Object> row=lockedLoan(id);
        if ("APPROVED".equals(row.get("la_status"))) return loan(id,null);
        if (!"PENDING_REVIEW".equals(row.get("la_status"))) throw conflict("Only pending applications can be approved");
        revalidate(row,http);
        jdbc.update("UPDATE loan_applications SET la_status='APPROVED',la_approved_amount=la_requested_amount,la_decision_admin_id=?,la_decided_at=UTC_TIMESTAMP(6),la_updated_at=UTC_TIMESTAMP(6) WHERE la_id=?",userId(actor),id);
        event(id,"APPROVED",userId(actor),null);
        return loan(id,null);
    }

    @PostMapping("/api/v1/admin/loans/{id}/reject") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Map<String,Object> reject(Authentication actor,@PathVariable long id,@Valid @RequestBody Rejection request) {
        Map<String,Object> row=lockedLoan(id);
        if ("REJECTED".equals(row.get("la_status"))) {
            if (request.reason().trim().equals(row.get("la_rejection_reason"))) return loan(id,null);
            throw conflict("Application has already been rejected with a different reason");
        }
        if (!"PENDING_REVIEW".equals(row.get("la_status"))) throw conflict("Only pending applications can be rejected");
        jdbc.update("UPDATE loan_applications SET la_status='REJECTED',la_rejection_reason=?,la_decision_admin_id=?,la_decided_at=UTC_TIMESTAMP(6),la_updated_at=UTC_TIMESTAMP(6) WHERE la_id=?",request.reason().trim(),userId(actor),id);
        event(id,"REJECTED",userId(actor),request.reason().trim());
        return loan(id,null);
    }

    @PostMapping("/api/v1/admin/loans/{id}/disburse") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Object disburse(Authentication actor,HttpServletRequest http,@PathVariable long id) {
        Map<String,Object> row=lockedLoan(id);
        if ("DISBURSED".equals(row.get("la_status"))) return loan(id,null);
        if (!"APPROVED".equals(row.get("la_status"))) throw conflict("Only approved applications can be disbursed");
        return org.springframework.http.ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message","Loan disbursement is unavailable until loan receivable accounting is implemented."));
    }

    @GetMapping("/api/v1/forex-requests")
    public List<Map<String,Object>> forexMine(Authentication actor) {
        return jdbc.queryForList(forexSelect()+" WHERE f.fr_customer_id=? ORDER BY f.fr_id DESC",userId(actor));
    }

    @GetMapping("/api/v1/forex-requests/{id}")
    public Map<String,Object> forexOne(Authentication actor,@PathVariable long id) {
        List<Map<String,Object>> rows=jdbc.queryForList(forexSelect()+" WHERE f.fr_id=? AND f.fr_customer_id=?",id,userId(actor));
        if(rows.isEmpty())throw notFound();return rows.get(0);
    }

    @PostMapping("/api/v1/forex-requests") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> forexRequest(Authentication actor,HttpServletRequest http,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max=255) String key,@RequestHeader(value="X-MPIN-Proof",required=false) String proof,@Valid @RequestBody ForexRequest request) {
        long customer=userId(actor);
        CustomerProductAccess.requireVerified(jdbc,customer);
        Long replay=jdbc.query("SELECT fr_id FROM forex_requests WHERE fr_customer_id=? AND fr_idempotency_key=?",rs->rs.next()?rs.getLong(1):null,customer,key);
        if (replay!=null) {
            Map<String,Object> existing=forexOne(actor,replay);
            if (((Number)existing.get("productId")).longValue()!=request.productId()
                    || ((BigDecimal)existing.get("amount")).compareTo(request.amount())!=0
                    || !request.fundingCurrencyCode().equalsIgnoreCase((String)existing.get("fundingCurrency"))
                    || !request.destinationCurrencyCode().equalsIgnoreCase((String)existing.get("destinationCurrency"))) throw conflict("Idempotency key already belongs to another forex request");
            return existing;
        }
        String proofJti=mpin.verify(actor,proof,"FOREX_REQUEST",customer,MpinProofVerifier.canonical("FOREX_REQUEST",request.productId(),request.amount(),request.fundingCurrencyCode(),request.destinationCurrencyCode()));
        requireCustomerActive(customer);
        JsonNode offer=offer("FOREX",request.productId(),http);
        String base=offer.path("baseCurrencyCode").asText();
        if (!base.equalsIgnoreCase(request.fundingCurrencyCode()) || base.equalsIgnoreCase(request.destinationCurrencyCode())) throw bad("Select the configured funding and destination currencies");
        Currency funding=currency(offer,base);
        Currency destination=currency(offer,request.destinationCurrencyCode());
        BigDecimal amount=amount(request.amount(),funding);
        BigDecimal markup=rate(offer,destination.code(),amount);
        mpin.consume(proofJti,customer,"FOREX_REQUEST");
        try {
            jdbc.update("INSERT INTO forex_requests(fr_customer_id,fr_product_id,fr_product_name,fr_funding_currency_id,fr_destination_currency_id,fr_amount,fr_markup_rate,fr_fee,fr_idempotency_key) VALUES(?,?,?,?,?,?,?,0,?)",
                    customer,request.productId(),offer.path("productName").asText(),funding.id(),destination.id(),amount,markup,key);
        } catch (DuplicateKeyException ex) { throw conflict("Forex request is already being processed"); }
        return forexOne(actor,jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class));
    }

    private JsonNode offer(String family,long id,HttpServletRequest request) {
        try {
            JsonNode body=products.get().uri("/api/v1/customer-products/{family}/{id}",family,id)
                    .header("Authorization",request.getHeader("Authorization")).retrieve().body(JsonNode.class);
            if(body==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Product catalog returned no offer");
            return body;
        } catch(RestClientResponseException ex) {
            if(ex.getStatusCode().value()==404)throw bad("Product is inactive, incomplete, or unavailable");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Product catalog could not validate this request");
        }
    }

    private Currency currency(JsonNode offer,String code) {
        for(JsonNode item:offer.path("currencies")) if(code.equalsIgnoreCase(item.path("code").asText())) {
            Long id=jdbc.query("SELECT fcr_currency_id FROM fd_currency_ref WHERE fcr_iso_code=?",rs->rs.next()?rs.getLong(1):null,item.path("code").asText());
            if(id==null)throw conflict("Currency reference has not arrived from Product Service");
            return new Currency(id,item.path("code").asText(),item.path("minimumAmount").decimalValue(),item.path("maximumAmount").decimalValue(),item.path("decimalPlaces").asInt());
        }
        throw bad("Currency is not configured for this product");
    }

    private BigDecimal amount(BigDecimal value,Currency currency) {
        BigDecimal amount;
        try { amount=value.setScale(currency.decimals(),RoundingMode.UNNECESSARY); }
        catch(ArithmeticException ex) { throw bad("Amount exceeds the currency decimal scale"); }
        if(amount.compareTo(currency.minimum())<0 || amount.compareTo(currency.maximum())>0)throw bad("Amount is outside the product limits");
        return amount;
    }

    private BigDecimal rate(JsonNode offer,String currency,BigDecimal fact) {
        for(JsonNode band:offer.path("pricingBands")) if(currency.equalsIgnoreCase(band.path("currencyCode").asText())
                && fact.compareTo(band.path("valueFrom").decimalValue())>=0
                && fact.compareTo(band.path("valueTo").decimalValue())<=0) return band.path("rate").decimalValue().setScale(8,RoundingMode.HALF_UP);
        throw bad("No configured pricing band covers this amount or tenure");
    }

    private BigDecimal requireSavings(long id,long customer,long currency,boolean lock) {
        String sql="SELECT sa_balance,sa_status FROM savings_accounts WHERE sa_account_id=? AND sa_holder_id=? AND sa_currency_id=?"+(lock?" FOR UPDATE":"");
        Map<String,Object> row=jdbc.query(sql,rs->rs.next()?Map.of("balance",rs.getBigDecimal(1),"status",rs.getString(2)):null,id,customer,currency);
        if(row==null || !"ACTIVE".equals(row.get("status")))throw bad("Destination savings account must be active, owned by the customer, and use the loan currency");
        return (BigDecimal)row.get("balance");
    }

    private void revalidate(Map<String,Object> row,HttpServletRequest http) {
        requireCustomerActive(((Number)row.get("la_customer_id")).longValue());
        long product=((Number)row.get("la_product_id")).longValue();
        JsonNode offer=offer("LOAN",product,http);
        String currency=jdbc.queryForObject("SELECT fcr_iso_code FROM fd_currency_ref WHERE fcr_currency_id=?",String.class,row.get("la_currency_id"));
        Currency terms=currency(offer,currency);
        amount((BigDecimal)row.get("la_requested_amount"),terms);
        rate(offer,currency,BigDecimal.valueOf(((Number)row.get("la_tenure_months")).intValue()));
        requireSavings(((Number)row.get("la_destination_savings_id")).longValue(),((Number)row.get("la_customer_id")).longValue(),terms.id(),false);
    }

    private String loanSelect() {return "SELECT a.la_id id,a.la_customer_id customerId,u.fur_full_name customerName,u.fur_customer_number customerNumber,a.la_product_id productId,a.la_product_name productName,a.la_requested_amount requestedAmount,a.la_approved_amount approvedAmount,c.fcr_iso_code currencyCode,a.la_tenure_months tenureMonths,a.la_annual_rate annualRate,a.la_purpose purpose,a.la_destination_savings_id destinationSavingsAccountId,s.sa_display_name destinationAccountName,s.sa_account_number destinationAccountNumber,a.la_status status,a.la_decision_admin_id decisionAdminId,a.la_decided_at decidedAt,a.la_rejection_reason rejectionReason,a.la_disbursement_transaction_id disbursementTransactionId,a.la_created_at createdAt,a.la_updated_at updatedAt FROM loan_applications a JOIN fd_user_ref u ON u.fur_user_id=a.la_customer_id JOIN fd_currency_ref c ON c.fcr_currency_id=a.la_currency_id JOIN savings_accounts s ON s.sa_account_id=a.la_destination_savings_id";}
    private Map<String,Object> loan(long id,Long owner) {List<Map<String,Object>> rows=owner==null?jdbc.queryForList(loanSelect()+" WHERE a.la_id=?",id):jdbc.queryForList(loanSelect()+" WHERE a.la_id=? AND a.la_customer_id=?",id,owner);if(rows.isEmpty())throw notFound();return rows.get(0);}
    private Map<String,Object> lockedLoan(long id) {Map<String,Object> row=jdbc.query("SELECT * FROM loan_applications WHERE la_id=? FOR UPDATE",rs->{if(!rs.next())return null;Map<String,Object> values=new HashMap<>();for(String field:List.of("la_customer_id","la_product_id","la_requested_amount","la_approved_amount","la_currency_id","la_tenure_months","la_destination_savings_id","la_status","la_rejection_reason"))values.put(field,rs.getObject(field));return values;},id);if(row==null)throw notFound();return row;}
    private void event(long id,String status,long actor,String reason){jdbc.update("INSERT INTO loan_application_events(le_application_id,le_status,le_actor_id,le_reason) VALUES(?,?,?,?)",id,status,actor,reason);}
    private List<Map<String,Object>> events(long id){return jdbc.queryForList("SELECT le_id id,le_status status,le_actor_id actorId,le_reason reason,le_created_at createdAt FROM loan_application_events WHERE le_application_id=? ORDER BY le_id",id);}
    private String forexSelect(){return "SELECT f.fr_id id,f.fr_product_id productId,f.fr_product_name productName,f.fr_amount amount,a.fcr_iso_code fundingCurrency,b.fcr_iso_code destinationCurrency,f.fr_markup_rate markupRate,f.fr_fee fee,f.fr_status status,f.fr_created_at createdAt FROM forex_requests f JOIN fd_currency_ref a ON a.fcr_currency_id=f.fr_funding_currency_id JOIN fd_currency_ref b ON b.fcr_currency_id=f.fr_destination_currency_id";}
    private long userId(Authentication actor){Object value=actor.getDetails() instanceof Map<?,?> details?details.get("userId"):null;if(!(value instanceof Number number)||number.longValue()<=0)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Token lacks customer identity");return number.longValue();}
    private void requireCustomerActive(long user){String status=jdbc.query("SELECT fur_user_status FROM fd_user_ref WHERE fur_user_id=?",rs->rs.next()?rs.getString(1):null,user);if(!"ACTIVE".equals(status))throw conflict("Customer account is not active");}
    private int scale(long currency){Integer result=jdbc.queryForObject("SELECT fcr_decimal_places FROM fd_currency_ref WHERE fcr_currency_id=?",Integer.class,currency);return result==null?2:result;}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Application or request not found");}
    private record Currency(long id,String code,BigDecimal minimum,BigDecimal maximum,int decimals){}
    public record LoanRequest(@NotNull @Min(1) Long productId,@NotNull @DecimalMin("0.01") BigDecimal amount,@NotBlank String currencyCode,@Min(1) int tenureMonths,@NotBlank @Size(max=500) String purpose,@NotNull @Min(1) Long destinationSavingsAccountId){}
    public record Rejection(@NotBlank @Size(max=500) String reason){}
    public record ForexRequest(@NotNull @Min(1) Long productId,@NotNull @DecimalMin("0.01") BigDecimal amount,@NotBlank String fundingCurrencyCode,@NotBlank String destinationCurrencyCode){}
}
