package com.bank.productservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api/v1/fd-products") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
public class FdOfferController {
    private static final Set<String> ALLOWED_COMPOUNDING = Set.of("MONTHLY", "QUARTERLY", "YEARLY");
    private static final Set<String> ALLOWED_PAYOUT = Set.of("AT_MATURITY");

    private final JdbcTemplate jdbc; private final ObjectMapper json; private final FixedDepositCategories fdCategories;
    public FdOfferController(JdbcTemplate jdbc,ObjectMapper json,FixedDepositCategories fdCategories){this.jdbc=jdbc;this.json=json;this.fdCategories=fdCategories;}

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    public OfferResponse create(@Valid @RequestBody OfferRequest request){
        ProductTextRules.requireIdentity(request.productName,request.description);
        validateBands(request);
        if(jdbc.queryForList("SELECT pro_product_id FROM product WHERE UPPER(pro_product_code)=UPPER(?)",Long.class,request.productCode.trim()).size()>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Product code already exists: "+request.productCode.trim().toUpperCase(Locale.ROOT));
        requireCategory(request.categoryId);
        if(!fdCategories.ids().contains(request.categoryId.intValue()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select a fixed-deposit category");
        final String base=(request.baseCurrencyCode==null?request.currencies.get(0).currencyCode:request.baseCurrencyCode).toUpperCase(Locale.ROOT);
        if(request.currencies.stream().noneMatch(c->c.currencyCode.equalsIgnoreCase(base)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Base currency must be in the configured currency schedules");
        if(jdbc.queryForList("SELECT cur_currency_id FROM currency WHERE UPPER(cur_iso_code)=UPPER(?) AND cur_is_active=1",Long.class,base).isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Base currency is not configured and active");
        String fromDate = request.effectiveFrom() == null ? null : request.effectiveFrom().toString();
        String toDate = request.effectiveTo() == null ? null : request.effectiveTo().toString();
        jdbc.update("INSERT INTO product(pro_product_code,pro_product_name,pro_category_id,pro_base_currency_code,pro_status,pro_effective_from,pro_effective_to,pro_description,pro_created_by) VALUES(?,?,?,?,?,?,?,?,?)",request.productCode.trim().toUpperCase(Locale.ROOT),ProductTextRules.name(request.productName),request.categoryId,base,"DRAFT",fromDate,toDate,request.description==null?null:request.description.trim(),"API");
        long productId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        jdbc.update("INSERT INTO product_translation(pt_product_id,pt_locale_tag,pt_product_name,pt_description) VALUES(?,?,?,?)",productId,"en",ProductTextRules.name(request.productName),request.description==null?null:request.description.trim());
        long ruleType=jdbc.queryForObject("SELECT rut_rule_type_id FROM rule_type WHERE rut_rule_type_code='FD_TENURE_RATE'",Long.class);
        int priority=1;
        for(CurrencySchedule schedule:request.currencies){
            String code=schedule.currencyCode.toUpperCase(Locale.ROOT);
            List<Long> ids=jdbc.queryForList("SELECT cur_currency_id FROM currency WHERE cur_iso_code=? AND cur_is_active=1",Long.class,code);
            if(ids.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Currency must be configured and active: "+code);
            long currencyId=ids.get(0);
            jdbc.update("INSERT INTO product_currency_config(pcc_product_id,pcc_currency_id,pcc_is_base_currency,pcc_minimum_amount,pcc_maximum_amount,pcc_created_by) VALUES(?,?,?,?,?,?)",productId,currencyId,code.equals(base),schedule.minimumAmount,schedule.maximumAmount,"API");
            if(schedule.prematurePenaltyPoints!=null){
                long penaltyRuleType=jdbc.queryForObject("SELECT rut_rule_type_id FROM rule_type WHERE rut_rule_type_code='FD_TENURE_RATE'",Long.class);
                jdbc.update("INSERT INTO product_rule(pr_product_id,pr_rule_type_id,pr_rule_name,pr_priority,pr_combine_logic,pr_is_active,pr_created_by) VALUES(?,?,?,?,?,1,?)",productId,penaltyRuleType,"CLOSURE_PENALTY",priority++,"AND","API");
                long penaltyRuleId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
                jdbc.update("INSERT INTO rule_outcome(ro_rule_id,ro_outcome_type,ro_outcome_value,ro_unit,ro_currency_id,ro_created_by) VALUES(?,?,?,?,?,?)",penaltyRuleId,"CLOSURE_PENALTY",schedule.prematurePenaltyPoints.toPlainString(),"PERCENTAGE_POINTS",currencyId,"API");
            }
            for(RateBand band:schedule.rateBands.stream().sorted(Comparator.comparing(RateBand::minimumTenureMonths)).toList()){
                jdbc.update("INSERT INTO product_rule(pr_product_id,pr_rule_type_id,pr_rule_name,pr_priority,pr_combine_logic,pr_is_active,pr_created_by) VALUES(?,?,?,?,?,1,?)",productId,ruleType,"TENURE_RATE",priority++,"AND","API");
                long ruleId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
                jdbc.update("INSERT INTO rule_condition(rc_rule_id,rc_condition_attribute,rc_operator,rc_value_from,rc_value_to,rc_created_by) VALUES(?,?,?,?,?,?)",ruleId,"TENURE_MONTHS","BETWEEN_INCLUSIVE",band.minimumTenureMonths.toString(),band.maximumTenureMonths.toString(),"API");
                jdbc.update("INSERT INTO rule_condition(rc_rule_id,rc_condition_attribute,rc_operator,rc_value_from,rc_value_to,rc_created_by) VALUES(?,?,?,?,?,?)",ruleId,"AMOUNT_RANGE","BETWEEN_INCLUSIVE",schedule.minimumAmount.toPlainString(),schedule.maximumAmount.toPlainString(),"API");
                String compounding = band.compoundingBasis() == null || band.compoundingBasis().isBlank() ? "QUARTERLY" : band.compoundingBasis().toUpperCase(Locale.ROOT);
                String payout = band.payoutFrequency() == null || band.payoutFrequency().isBlank() ? "AT_MATURITY" : band.payoutFrequency().toUpperCase(Locale.ROOT);
                jdbc.update("INSERT INTO rule_outcome(ro_rule_id,ro_outcome_type,ro_outcome_value,ro_unit,ro_compounding_basis,ro_payout_frequency,ro_currency_id,ro_created_by) VALUES(?,?,?,?,?,?,?,?)",ruleId,"INTEREST_RATE",band.annualRate,"DECIMAL_FRACTION",compounding,payout,currencyId,"API");
            }
        }
        return response(productId);
    }

    @GetMapping public List<OfferResponse> list(){Set<Integer> ids=fdCategories.ids();if(ids.isEmpty())return List.of();String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));return jdbc.queryForList("SELECT pro_product_id FROM product WHERE pro_category_id IN ("+placeholders+") ORDER BY pro_product_id",Long.class,ids.toArray()).stream().map(this::response).toList();}

    @PutMapping("/{productId}") @Transactional
    public OfferResponse update(@PathVariable long productId,@Valid @RequestBody ProductUpdateRequest request){
        OfferResponse existing=response(productId);
        if (!ProductTextRules.validName(request.productName(),255)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid product name (3-255 characters including a letter).");
        if (!ProductTextRules.validDescription(request.description())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter 10-1000 characters including a letter, without markup or control characters.");
        jdbc.update("UPDATE product SET pro_product_name=?,pro_description=?,pro_event_version=pro_event_version+1,pro_updated_by='API',pro_updated_date=UTC_TIMESTAMP(6) WHERE pro_product_id=?",ProductTextRules.name(request.productName()),request.description() == null ? null : request.description().trim(),productId);
        jdbc.update("INSERT INTO product_translation(pt_product_id,pt_locale_tag,pt_product_name,pt_description) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE pt_product_name=VALUES(pt_product_name),pt_description=VALUES(pt_description),pt_updated_date=UTC_TIMESTAMP(6)",productId,"en",ProductTextRules.name(request.productName()),request.description() == null ? null : request.description().trim());
        writeOutbox(productId);
        return response(existing.fdProductId());
    }

    @PostMapping("/{productId}/activate") @Transactional
    public OfferResponse activate(@PathVariable long productId){
        OfferResponse offer=response(productId);
        if("ACTIVE".equals(offer.status()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Product is already active");
        if(!"DRAFT".equals(offer.status()) && !"INACTIVE".equals(offer.status()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only a draft or inactive FD product can be activated");
        List<Map<String,Object>> dates=jdbc.queryForList("SELECT pro_effective_from,pro_effective_to FROM product WHERE pro_product_id=?",productId);
        if(!dates.isEmpty()){
            Object toObj=dates.get(0).get("pro_effective_to");
            if(toObj!=null&&!toObj.toString().isBlank()){
                try{
                    LocalDate toDate=LocalDate.parse(toObj.toString().trim());
                    if(toDate.isBefore(LocalDate.now())){
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Product effective period has already expired");
                    }
                }catch(Exception ignored){}
            }
        }
        List<Map<String,Object>> configs=jdbc.queryForList("SELECT p.pcc_currency_id,p.pcc_is_base_currency,c.cur_iso_code,c.cur_decimal_places,p.pcc_minimum_amount,p.pcc_maximum_amount FROM product_currency_config p JOIN currency c ON c.cur_currency_id=p.pcc_currency_id WHERE p.pcc_product_id=? AND c.cur_is_active=1",productId);
        if(configs.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"FD offer needs an active currency configuration");
        long baseCount=configs.stream().filter(c->Boolean.TRUE.equals(c.get("pcc_is_base_currency"))).count();
        if(baseCount!=1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"FD offer must have exactly one base currency");
        for(Map<String,Object> config:configs){
            int rates=jdbc.queryForObject("SELECT COUNT(*) FROM product_rule r JOIN rule_outcome o ON o.ro_rule_id=r.pr_rule_id WHERE r.pr_product_id=? AND r.pr_rule_name='TENURE_RATE' AND r.pr_is_active=1 AND o.ro_outcome_type='INTEREST_RATE' AND o.ro_currency_id=?",Integer.class,productId,config.get("pcc_currency_id"));
            if(rates==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Currency "+config.get("cur_iso_code")+" needs at least one interest rate band");
        }
        jdbc.update("UPDATE product SET pro_status='ACTIVE',pro_event_version=pro_event_version+1,pro_updated_by='API',pro_updated_date=UTC_TIMESTAMP(6) WHERE pro_product_id=?",productId);
        writeOutbox(productId);
        return response(productId);
    }

    @PostMapping("/{productId}/deactivate") @Transactional
    public OfferResponse deactivate(@PathVariable long productId){
        OfferResponse offer=response(productId);
        if(!"ACTIVE".equals(offer.status()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only an active FD product can be deactivated");
        jdbc.update("UPDATE product SET pro_status='INACTIVE',pro_event_version=pro_event_version+1,pro_updated_by='API',pro_updated_date=UTC_TIMESTAMP(6) WHERE pro_product_id=?",productId);
        writeOutbox(productId);
        return response(productId);
    }

    private OfferResponse response(long id){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT p.pro_product_id,p.pro_product_code,p.pro_product_name,p.pro_status,p.pro_category_id FROM product p WHERE p.pro_product_id=?",id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"FD product not found"); Map<String,Object> p=rows.get(0);
        if(!fdCategories.ids().contains(((Number)p.get("pro_category_id")).intValue()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"FD product not found");
        List<String> currencies=jdbc.queryForList("SELECT c.cur_iso_code FROM product_currency_config p JOIN currency c ON c.cur_currency_id=p.pcc_currency_id WHERE p.pcc_product_id=? ORDER BY c.cur_iso_code",String.class,id);
        return new OfferResponse(id,id,(String)p.get("pro_product_code"),(String)p.get("pro_product_name"),(String)p.get("pro_status"),currencies);
    }
    private void requireCategory(long id){if(jdbc.queryForList("SELECT pc_category_id FROM product_category WHERE pc_category_id=?",Long.class,id).isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Product category does not exist");}
    private void validateBands(OfferRequest r){
        if(r.effectiveFrom()!=null && r.effectiveTo()!=null && r.effectiveFrom().isAfter(r.effectiveTo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Effective-to date must be on or after effective-from date.");
        }
        if(r.currencies==null||r.currencies.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Configure at least one currency schedule");Set<String> seen=new HashSet<>();
        for(CurrencySchedule c:r.currencies){
            if(!seen.add(c.currencyCode.toUpperCase(Locale.ROOT)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Currency schedule is duplicated");
            if(c.minimumAmount.compareTo(c.maximumAmount)>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Minimum amount exceeds maximum amount");
            if(c.prematurePenaltyPoints!=null){
                if(c.prematurePenaltyPoints.signum()<0||c.prematurePenaltyPoints.compareTo(new BigDecimal("100"))>0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Premature penalty must be between 0 and 100 percentage points");
                }
                if(c.prematurePenaltyPoints.scale()>4) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Premature penalty supports at most four decimal places");
                }
            }
            if(c.rateBands==null||c.rateBands.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Configure at least one rate band");
            List<RateBand> bands=c.rateBands.stream().sorted(Comparator.comparing(RateBand::minimumTenureMonths)).toList();
            for(int i=0;i<bands.size();i++){
                RateBand b=bands.get(i);
                if(b.minimumTenureMonths>b.maximumTenureMonths)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tenure range is inverted");
                if(b.annualRate.scale()>4)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Rate supports at most four decimal places");
                if(b.annualRate.signum()<0||b.annualRate.compareTo(BigDecimal.ONE)>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Annual rate must be between 0.0000 and 1.0000");
                if(i>0&&b.minimumTenureMonths!=bands.get(i-1).maximumTenureMonths+1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tenure bands must be contiguous and non-overlapping");
                if(b.compoundingBasis()!=null&&!b.compoundingBasis().isBlank()&&!ALLOWED_COMPOUNDING.contains(b.compoundingBasis().toUpperCase(Locale.ROOT))){
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Compounding basis must be one of: "+ALLOWED_COMPOUNDING);
                }
                if(b.payoutFrequency()!=null&&!b.payoutFrequency().isBlank()&&!ALLOWED_PAYOUT.contains(b.payoutFrequency().toUpperCase(Locale.ROOT))){
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Payout frequency must be one of: "+ALLOWED_PAYOUT);
                }
            }
        }
    }
    private void writeOutbox(long id){
        Map<String,Object> product=jdbc.queryForMap("SELECT pro_product_code,pro_product_name,pro_description,pro_base_currency_code,pro_status,pro_event_version FROM product WHERE pro_product_id=?",id);
        List<Map<String,Object>> currency=jdbc.queryForList("SELECT c.cur_currency_id,c.cur_iso_code,c.cur_currency_name,c.cur_decimal_places,p.pcc_minimum_amount,p.pcc_maximum_amount,p.pcc_is_base_currency,(SELECT o.ro_outcome_value FROM product_rule pr JOIN rule_outcome o ON o.ro_rule_id=pr.pr_rule_id WHERE pr.pr_product_id=p.pcc_product_id AND o.ro_currency_id=p.pcc_currency_id AND o.ro_outcome_type='CLOSURE_PENALTY' AND pr.pr_is_active=1 ORDER BY pr.pr_priority LIMIT 1) premature_penalty_points FROM product_currency_config p JOIN currency c ON c.cur_currency_id=p.pcc_currency_id WHERE p.pcc_product_id=? ORDER BY c.cur_iso_code",id);
        List<Map<String,Object>> bands=jdbc.queryForList("SELECT c.cur_iso_code,o.ro_outcome_value,o.ro_compounding_basis,o.ro_payout_frequency,MAX(CASE WHEN q.rc_condition_attribute='TENURE_MONTHS' THEN q.rc_value_from END) minimumMonth,MAX(CASE WHEN q.rc_condition_attribute='TENURE_MONTHS' THEN q.rc_value_to END) maximumMonth FROM product_rule r JOIN rule_outcome o ON o.ro_rule_id=r.pr_rule_id JOIN currency c ON c.cur_currency_id=o.ro_currency_id JOIN rule_condition q ON q.rc_rule_id=r.pr_rule_id WHERE r.pr_product_id=? AND r.pr_is_active=1 AND o.ro_outcome_type='INTEREST_RATE' GROUP BY r.pr_rule_id,o.ro_outcome_value,o.ro_compounding_basis,o.ro_payout_frequency,c.cur_iso_code ORDER BY c.cur_iso_code,minimumMonth",id);
        Map<String,Object> payload=Map.of("eventId",UUID.randomUUID().toString(),"eventType","PRODUCT_CATALOG","schemaVersion",1,"occurredAt",java.time.Instant.now().toString(),"aggregateId",Long.toString(id),"sourceVersion",product.get("pro_event_version"),"product",product,"currencies",currency,"rateBands",bands);
        try{jdbc.update("INSERT INTO outbox_event(event_key,event_type,topic,source_version,payload) VALUES(?,?,?,?,CAST(? AS JSON))",Long.toString(id),"PRODUCT_CATALOG","product.catalog",product.get("pro_event_version"),json.writeValueAsString(payload));}catch(Exception e){throw new IllegalStateException("Unable to write catalog event",e);}
    }

    void publishActiveSnapshots() {
        Set<Integer> categoryIds = fdCategories.ids();
        if (categoryIds.isEmpty()) return;
        String placeholders = String.join(",", Collections.nCopies(categoryIds.size(), "?"));
        List<Long> activeIds = jdbc.queryForList("SELECT pro_product_id FROM product WHERE pro_status='ACTIVE' AND pro_category_id IN (" + placeholders + ") ORDER BY pro_product_id", Long.class, categoryIds.toArray());
        for (long id : activeIds) {
            jdbc.update("UPDATE product SET pro_event_version=1 WHERE pro_product_id=? AND pro_event_version=0", id);
            writeOutbox(id);
        }
    }

    public record OfferRequest(@NotBlank @Size(max=50) String productCode,@NotBlank @Size(max=255) String productName,@NotNull @Min(1) Long categoryId,@Size(max=1000) String description,String baseCurrencyCode,@NotEmpty @Valid List<CurrencySchedule> currencies,LocalDate effectiveFrom,LocalDate effectiveTo){
        public OfferRequest(String productCode, String productName, Long categoryId, String description, String baseCurrencyCode, List<CurrencySchedule> currencies) {
            this(productCode, productName, categoryId, description, baseCurrencyCode, currencies, null, null);
        }
    }
    public record ProductUpdateRequest(@NotBlank @Size(max=255) String productName,@Size(max=1000) String description){}
    public record CurrencySchedule(@NotBlank String currencyCode,@NotNull @DecimalMin("0.01") BigDecimal minimumAmount,@NotNull @DecimalMin("0.01") BigDecimal maximumAmount,@NotEmpty @Valid List<RateBand> rateBands,BigDecimal prematurePenaltyPoints){}
    public record RateBand(@NotNull @Min(1) Integer minimumTenureMonths,@NotNull @Min(1) Integer maximumTenureMonths,@NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal annualRate,String compoundingBasis,String payoutFrequency){
        public RateBand(Integer minimumTenureMonths, Integer maximumTenureMonths, BigDecimal annualRate) {
            this(minimumTenureMonths, maximumTenureMonths, annualRate, "QUARTERLY", "AT_MATURITY");
        }
    }
    public record OfferResponse(@JsonProperty("fd_product_id") Long fdProductId,@JsonProperty("catalog_product_id") Long catalogProductId,@JsonProperty("product_code") String productCode,@JsonProperty("product_name") String productName,String status,@JsonProperty("currency_codes") List<String> currencyCodes){}
}
