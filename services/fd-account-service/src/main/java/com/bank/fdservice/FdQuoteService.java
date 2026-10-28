package com.bank.fdservice;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** One authority for preview and booking; queries only, with no writes or locks. */
final class FdQuoteService {
    private final JdbcTemplate jdbc;
    private final DemoClockService clock;
    FdQuoteService(JdbcTemplate jdbc,DemoClockService clock){this.jdbc=jdbc;this.clock=clock;}
    Quote resolve(FixedDepositController.BookingRequest request,Long userId){
        Map<String,Object> product=jdbc.query("SELECT fpr_product_code,fpr_product_name,fpr_status FROM fd_product_ref WHERE fpr_product_id=?",r->r.next()?Map.of("code",r.getString(1),"name",r.getString(2),"status",r.getString(3)):null,request.productId());
        if(product==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Fixed deposit product not found");
        if(!"ACTIVE".equalsIgnoreCase((String)product.get("status")))throw bad("Fixed deposit product is not active");
        Map<String,Object> currency=jdbc.query("SELECT fcr_currency_id,fcr_decimal_places FROM fd_currency_ref WHERE UPPER(fcr_iso_code)=UPPER(?)",r->r.next()?Map.of("id",r.getLong(1),"scale",r.getInt(2)):null,request.currencyCode());
        if(currency==null)throw bad("Currency reference has not arrived from the Product service");
        long currencyId=((Number)currency.get("id")).longValue();int scale=((Number)currency.get("scale")).intValue();
        if(scale<0||scale>8)throw bad("Currency decimal scale is invalid");
        if(request.principal().compareTo(BigDecimal.ZERO)<=0)throw bad("Principal amount must be positive");
        BigDecimal principal;try{principal=request.principal().setScale(scale,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw bad("Principal exceeds the currency decimal scale");}
        if(request.tenureMonths()<1)throw bad("Tenure months must be at least 1");
        if(userId!=null) {
        Map<String,Object> ownerAccount=jdbc.query("SELECT sa_account_id FROM savings_accounts WHERE sa_account_id=? AND sa_holder_id=? AND sa_currency_id=? AND sa_status='ACTIVE'",r->r.next()?Map.of("id",r.getLong(1)):null,request.savingsAccountId(),userId,currencyId);
        if(ownerAccount==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Eligible owned savings account not found");
        }
        Map<String,Object> pcc=jdbc.query("SELECT minimum_amount,maximum_amount,COALESCE(premature_penalty_points,1.0000) FROM fd_product_currency_ref WHERE product_id=? AND currency_id=?",r->r.next()?Map.of("min",r.getBigDecimal(1),"max",r.getBigDecimal(2),"penalty",r.getBigDecimal(3)==null?"":r.getBigDecimal(3).toPlainString()):null,request.productId(),currencyId);
        if(pcc==null)throw bad("Currency "+request.currencyCode()+" is not supported for this product");
        BigDecimal minAmount=(BigDecimal)pcc.get("min");BigDecimal maxAmount=(BigDecimal)pcc.get("max");
        if(principal.compareTo(minAmount)<0||principal.compareTo(maxAmount)>0)throw bad("Principal amount "+principal+" is outside permitted bounds ["+minAmount+", "+maxAmount+"]");
        LocalDate open=clock.businessDate();
        List<Map<String,Object>> candidateBands=jdbc.queryForList("SELECT annual_rate,compounding_basis,payout_frequency,effective_from,effective_to FROM fd_product_rate_ref WHERE product_id=? AND currency_id=? AND minimum_tenure_months<=? AND maximum_tenure_months>=?",request.productId(),currencyId,request.tenureMonths(),request.tenureMonths());
        if(candidateBands.isEmpty())throw bad("Requested tenure of "+request.tenureMonths()+" months does not match any configured tenure rate band");
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
        if(request.expectedAnnualRate()!=null && rate.compareTo(request.expectedAnnualRate())!=0){
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Disclosed interest rate ("+request.expectedAnnualRate()+") differs from authoritative rate ("+rate+") on "+open+". Please review the updated disclosure before booking.");
        }
        String penaltyStr=(String)pcc.get("penalty");
        LocalDate maturity=open.plusMonths(request.tenureMonths());BigDecimal estimate=FdCompounding.maturity(principal,rate,open,maturity,scale,compounding);
        String policy="academic-v2-"+FdCompounding.basis(compounding).toLowerCase(java.util.Locale.ROOT);
        return new Quote(product,currencyId,scale,principal,rate,compounding,payout,penaltyStr,open,maturity,estimate,policy);
    }
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    record Quote(Map<String,Object> product,long currencyId,int scale,BigDecimal principal,BigDecimal rate,String compounding,String payout,String penaltyStr,LocalDate open,LocalDate maturity,BigDecimal estimate,String policy){}
}