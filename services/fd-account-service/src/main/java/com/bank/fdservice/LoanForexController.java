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

/** Loan decisions and customer-owned forex conversions live beside the savings ledger they affect. */
@RestController
public class LoanForexController {
    private static final BigDecimal FOREX_BANK_FEE_RATE = new BigDecimal("0.01");
    private final JdbcTemplate jdbc;
    private final RestClient products;
    private final MpinProofVerifier mpin;
    private final PostingService postings;
    private final Map<String,BigDecimal> demoReferenceRates;

    public LoanForexController(JdbcTemplate jdbc, @Value("${app.product.base-url:http://localhost:8082}") String productUrl,
            MpinProofVerifier mpin, PostingService postings,
            @Value("${app.forex.reference-rates:INR/USD=83.25}") String referenceRates) {
        this.jdbc = jdbc;
        this.products = RestClient.create(productUrl);
        this.mpin=mpin;
        this.postings=postings;
        this.demoReferenceRates=parseDemoReferenceRates(referenceRates);
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
        CustomerProductAccess.requireVerified(customer);
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
    public Map<String,Object> disburse(Authentication actor,HttpServletRequest http,@PathVariable long id) {
        Map<String,Object> row=lockedLoan(id);
        if ("DISBURSED".equals(row.get("la_status"))) return loan(id,null);
        if (!"APPROVED".equals(row.get("la_status"))) throw conflict("Only approved applications can be disbursed");
        revalidate(row,http);
        long customer=((Number)row.get("la_customer_id")).longValue();
        long savings=((Number)row.get("la_destination_savings_id")).longValue();
        long currency=((Number)row.get("la_currency_id")).longValue();
        BigDecimal approved=(BigDecimal)row.get("la_approved_amount");
        if (approved==null) throw conflict("Approved loan amount is unavailable");
        requireSavings(savings,customer,currency,true);
        String reference="LOAN:"+id;
        postings.postSavingsMovement(savings,currency,approved,"CREDIT",reference,"Loan disbursement #"+id,
                actor.getName(),"LOAN_DISBURSEMENT:"+id,"LOAN_DISBURSEMENT","LOAN_RECEIVABLE","SAVINGS");
        Long transactionId=jdbc.queryForObject("SELECT st_id FROM savings_transactions WHERE st_reference_id=? AND st_account_id=? AND st_entry_type='CREDIT'",Long.class,reference,savings);
        jdbc.update("UPDATE loan_applications SET la_status='DISBURSED',la_disbursement_transaction_id=?,la_updated_at=UTC_TIMESTAMP(6) WHERE la_id=?",transactionId,id);
        event(id,"DISBURSED",userId(actor),null);
        return loan(id,null);
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

    @GetMapping("/api/v1/forex-quote")
    public ForexQuote forexQuote(Authentication actor,HttpServletRequest http,
            @RequestParam @Min(1) long productId,@RequestParam @DecimalMin("0.01") BigDecimal amount,
            @RequestParam @NotBlank String fundingCurrencyCode,@RequestParam @NotBlank String destinationCurrencyCode,
            @RequestParam @Min(1) long sourceSavingsAccountId,@RequestParam @Min(1) long destinationSavingsAccountId) {
        long customer=userId(actor);
        CustomerProductAccess.requireVerified(customer);
        requireCustomerActive(customer);
        ForexTerms terms=forexTerms(productId,amount,fundingCurrencyCode,destinationCurrencyCode,http);
        ForexAccountChoice source=requireForexAccounts(customer,sourceSavingsAccountId,destinationSavingsAccountId,terms.funding(),terms.destination());
        if(source.balance().compareTo(terms.totalDebit())<0) throw conflict("Insufficient funds for the conversion and fee");
        return new ForexQuote(terms.amount(),terms.funding().code(),terms.destination().code(),terms.referenceRate(),
                terms.markupRate(),terms.customerRate(),terms.fee(),terms.totalDebit(),terms.destinationAmount(),"STATIC_DEMO");
    }

    @PostMapping("/api/v1/forex-requests") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> forexRequest(Authentication actor,HttpServletRequest http,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max=255) String key,
            @RequestHeader(value="X-MPIN-Proof",required=false) String proof,@Valid @RequestBody ForexRequest request) {
        long customer=userId(actor);
        CustomerProductAccess.requireVerified(customer);
        Long replay=jdbc.query("SELECT fr_id FROM forex_requests WHERE fr_customer_id=? AND fr_idempotency_key=?",rs->rs.next()?rs.getLong(1):null,customer,key);
        if (replay!=null) {
            Map<String,Object> existing=forexOne(actor,replay);
            Object oldSource=existing.get("sourceSavingsAccountId"),oldDestination=existing.get("destinationSavingsAccountId");
            if (((Number)existing.get("productId")).longValue()!=request.productId()
                    || ((BigDecimal)existing.get("amount")).compareTo(request.amount())!=0
                    || !request.fundingCurrencyCode().equalsIgnoreCase((String)existing.get("fundingCurrency"))
                    || !request.destinationCurrencyCode().equalsIgnoreCase((String)existing.get("destinationCurrency"))
                    || !(oldSource instanceof Number sourceId) || sourceId.longValue()!=request.sourceSavingsAccountId()
                    || !(oldDestination instanceof Number destinationId) || destinationId.longValue()!=request.destinationSavingsAccountId()
                    || !sameDecimal(existing.get("referenceRate"),request.referenceRate())
                    || !sameDecimal(existing.get("markupRate"),request.markupRate())
                    || !sameDecimal(existing.get("customerRate"),request.customerRate())
                    || !sameDecimal(existing.get("fee"),request.fee())
                    || !sameDecimal(existing.get("totalDebit"),request.totalDebit())
                    || !sameDecimal(existing.get("destinationAmount"),request.destinationAmount()))
                throw conflict("Idempotency key already belongs to another forex conversion");
            return existing;
        }
        String proofJti=mpin.verify(actor,proof,"FOREX_REQUEST",customer,MpinProofVerifier.canonical("FOREX_REQUEST",
                request.productId(),request.amount(),request.fundingCurrencyCode(),request.destinationCurrencyCode(),
                request.sourceSavingsAccountId(),request.destinationSavingsAccountId(),request.referenceRate(),request.markupRate(),
                request.customerRate(),request.fee(),request.totalDebit(),request.destinationAmount()));
        requireCustomerActive(customer);
        ForexTerms terms=forexTerms(request.productId(),request.amount(),request.fundingCurrencyCode(),request.destinationCurrencyCode(),http);
        if(!sameDecimal(terms.referenceRate(),request.referenceRate())||!sameDecimal(terms.markupRate(),request.markupRate())
                ||!sameDecimal(terms.customerRate(),request.customerRate())||!sameDecimal(terms.fee(),request.fee())
                ||!sameDecimal(terms.totalDebit(),request.totalDebit())||!sameDecimal(terms.destinationAmount(),request.destinationAmount()))
            throw conflict("Forex quote changed; calculate a new quote before confirming");
        ForexAccountChoice source=requireForexAccounts(customer,request.sourceSavingsAccountId(),request.destinationSavingsAccountId(),terms.funding(),terms.destination());
        if(source.balance().compareTo(terms.totalDebit())<0) throw conflict("Insufficient funds for the conversion and fee");
        mpin.consume(proofJti,customer,"FOREX_REQUEST");
        try {
            jdbc.update("INSERT INTO forex_requests(fr_customer_id,fr_product_id,fr_product_name,fr_funding_currency_id,fr_destination_currency_id,fr_amount,fr_markup_rate,fr_fee,fr_reference_rate,fr_customer_rate,fr_destination_amount,fr_total_debit,fr_source_savings_account_id,fr_destination_savings_account_id,fr_status,fr_idempotency_key) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,'COMPLETED',?)",
                    customer,request.productId(),terms.productName(),terms.funding().id(),terms.destination().id(),terms.amount(),
                    terms.markupRate(),terms.fee(),terms.referenceRate(),terms.customerRate(),terms.destinationAmount(),terms.totalDebit(),
                    request.sourceSavingsAccountId(),request.destinationSavingsAccountId(),key);
        } catch (DuplicateKeyException ex) { throw conflict("Forex conversion is already being processed"); }
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        postings.postForexConversion(customer,request.sourceSavingsAccountId(),request.destinationSavingsAccountId(),
                terms.funding().id(),terms.destination().id(),terms.amount(),terms.fee(),terms.destinationAmount(),
                "FOREX:"+id,actor.getName());
        return forexOne(actor,id);
    }

    private ForexTerms forexTerms(long productId,BigDecimal requestedAmount,String fundingCode,String destinationCode,HttpServletRequest http) {
        JsonNode offer=offer("FOREX",productId,http);
        String base=offer.path("baseCurrencyCode").asText();
        if (!base.equalsIgnoreCase(fundingCode) || base.equalsIgnoreCase(destinationCode)) throw bad("Select the configured funding and destination currencies");
        Currency funding=currency(offer,base);
        Currency destination=currency(offer,destinationCode);
        BigDecimal principal=amount(requestedAmount,funding);
        BigDecimal markup=rate(offer,destination.code(),principal);
        BigDecimal reference=demoReferenceRates.get(funding.code()+"/"+destination.code());
        if(reference==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No static demo reference rate is configured for "+funding.code()+"/"+destination.code());
        BigDecimal customerRate=reference.multiply(BigDecimal.ONE.add(markup)).setScale(12,RoundingMode.HALF_UP);
        BigDecimal converted=principal.divide(customerRate,destination.decimals(),RoundingMode.HALF_UP);
        amount(converted,destination);
        BigDecimal fee=principal.multiply(FOREX_BANK_FEE_RATE).setScale(funding.decimals(),RoundingMode.HALF_UP);
        BigDecimal totalDebit=principal.add(fee).setScale(funding.decimals(),RoundingMode.UNNECESSARY);
        return new ForexTerms(offer.path("productName").asText(),funding,destination,principal,markup,reference,customerRate,fee,totalDebit,converted);
    }

    private ForexAccountChoice requireForexAccounts(long customer,long sourceId,long destinationId,Currency funding,Currency destination) {
        if(sourceId==destinationId) throw bad("Source and destination savings accounts must differ");
        List<ForexAccountChoice> accounts=jdbc.query(
                "SELECT sa_account_id,sa_currency_id,sa_status,sa_balance FROM savings_accounts WHERE sa_account_id IN (?,?) AND sa_holder_id=? ORDER BY sa_account_id",
                (rs,n)->new ForexAccountChoice(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getBigDecimal(4)),sourceId,destinationId,customer);
        if(accounts.size()!=2) throw bad("Choose two savings accounts owned by you");
        ForexAccountChoice source=accounts.stream().filter(account->account.id()==sourceId).findFirst().orElseThrow();
        ForexAccountChoice target=accounts.stream().filter(account->account.id()==destinationId).findFirst().orElseThrow();
        if(!"ACTIVE".equals(source.status())||!"ACTIVE".equals(target.status())) throw bad("Both savings accounts must be active");
        if(source.currencyId()!=funding.id()||target.currencyId()!=destination.id()) throw bad("Selected account currencies do not match the conversion currencies");
        return source;
    }

    private Map<String,BigDecimal> parseDemoReferenceRates(String configured) {
        Map<String,BigDecimal> rates=new HashMap<>();
        if(configured==null||configured.isBlank()) return Map.of();
        for(String entry:configured.split(",")) {
            String[] pair=entry.trim().split("=",2);
            if(pair.length!=2||!pair[0].contains("/")) throw new IllegalArgumentException("Forex demo rates must use FUNDING/DESTINATION=RATE format");
            String key=pair[0].trim().toUpperCase(java.util.Locale.ROOT);
            BigDecimal value=new BigDecimal(pair[1].trim());
            if(value.signum()<=0) throw new IllegalArgumentException("Forex demo reference rates must be positive");
            rates.put(key,value.setScale(12,RoundingMode.HALF_UP));
        }
        return Map.copyOf(rates);
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
    private String forexSelect(){return "SELECT f.fr_id id,f.fr_product_id productId,f.fr_product_name productName,f.fr_amount amount,a.fcr_iso_code fundingCurrency,b.fcr_iso_code destinationCurrency,f.fr_markup_rate markupRate,f.fr_fee fee,f.fr_reference_rate referenceRate,f.fr_customer_rate customerRate,f.fr_destination_amount destinationAmount,f.fr_total_debit totalDebit,f.fr_source_savings_account_id sourceSavingsAccountId,f.fr_destination_savings_account_id destinationSavingsAccountId,CASE WHEN s.sa_account_number IS NULL THEN NULL ELSE CONCAT('••••',RIGHT(s.sa_account_number,4)) END sourceAccount,CASE WHEN d.sa_account_number IS NULL THEN NULL ELSE CONCAT('••••',RIGHT(d.sa_account_number,4)) END destinationAccount,f.fr_status status,f.fr_created_at createdAt FROM forex_requests f JOIN fd_currency_ref a ON a.fcr_currency_id=f.fr_funding_currency_id JOIN fd_currency_ref b ON b.fcr_currency_id=f.fr_destination_currency_id LEFT JOIN savings_accounts s ON s.sa_account_id=f.fr_source_savings_account_id LEFT JOIN savings_accounts d ON d.sa_account_id=f.fr_destination_savings_account_id";}
    private long userId(Authentication actor){Object value=actor.getDetails() instanceof Map<?,?> details?details.get("userId"):null;if(!(value instanceof Number number)||number.longValue()<=0)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Token lacks customer identity");return number.longValue();}
    private void requireCustomerActive(long user){String status=jdbc.query("SELECT fur_user_status FROM fd_user_ref WHERE fur_user_id=?",rs->rs.next()?rs.getString(1):null,user);if(!"ACTIVE".equals(status))throw conflict("Customer account is not active");}
    private int scale(long currency){Integer result=jdbc.queryForObject("SELECT fcr_decimal_places FROM fd_currency_ref WHERE fcr_currency_id=?",Integer.class,currency);return result==null?2:result;}
    private boolean sameDecimal(Object stored,BigDecimal requested){return stored instanceof BigDecimal value&&value.compareTo(requested)==0;}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Application or request not found");}
    private record Currency(long id,String code,BigDecimal minimum,BigDecimal maximum,int decimals){}
    private record ForexAccountChoice(long id,long currencyId,String status,BigDecimal balance){}
    private record ForexTerms(String productName,Currency funding,Currency destination,BigDecimal amount,
            BigDecimal markupRate,BigDecimal referenceRate,BigDecimal customerRate,BigDecimal fee,
            BigDecimal totalDebit,BigDecimal destinationAmount){}
    public record ForexQuote(BigDecimal amount,String fundingCurrency,String destinationCurrency,
            BigDecimal referenceRate,BigDecimal markupRate,BigDecimal customerRate,BigDecimal fee,
            BigDecimal totalDebit,BigDecimal destinationAmount,String quoteType){}
    public record LoanRequest(@NotNull @Min(1) Long productId,@NotNull @DecimalMin("0.01") BigDecimal amount,@NotBlank String currencyCode,@Min(1) int tenureMonths,@NotBlank @Size(max=500) String purpose,@NotNull @Min(1) Long destinationSavingsAccountId){}
    public record Rejection(@NotBlank @Size(max=500) String reason){}
    public record ForexRequest(@NotNull @Min(1) Long productId,@NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank String fundingCurrencyCode,@NotBlank String destinationCurrencyCode,
            @NotNull @Min(1) Long sourceSavingsAccountId,@NotNull @Min(1) Long destinationSavingsAccountId,
            @NotNull @DecimalMin("0.000000000001") BigDecimal referenceRate,@NotNull @DecimalMin("0.0") BigDecimal markupRate,
            @NotNull @DecimalMin("0.000000000001") BigDecimal customerRate,@NotNull @DecimalMin("0.0") BigDecimal fee,
            @NotNull @DecimalMin("0.01") BigDecimal totalDebit,@NotNull @DecimalMin("0.01") BigDecimal destinationAmount){}
}
