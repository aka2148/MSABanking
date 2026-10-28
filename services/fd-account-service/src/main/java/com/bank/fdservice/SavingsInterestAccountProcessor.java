package com.bank.fdservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SavingsInterestAccountProcessor {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final PostingService postings;
    public SavingsInterestAccountProcessor(JdbcTemplate jdbc,ObjectMapper json,PostingService postings){this.jdbc=jdbc;this.json=json;this.postings=postings;}

    @Transactional
    public Result process(long id,LocalDate date,boolean quarterly){
        Map<String,Object> a=jdbc.query("SELECT sa_currency_id,sa_balance,sa_status,sa_interest_terms,sa_interest_start_date FROM savings_accounts WHERE sa_account_id=? FOR UPDATE",r->r.next()?Map.of("currency",r.getLong(1),"balance",r.getBigDecimal(2),"status",r.getString(3),"terms",r.getString(4)==null?"":r.getString(4),"start",r.getDate(5)==null?LocalDate.MIN:r.getDate(5).toLocalDate()):null,id);
        if(a==null||"CLOSED".equals(a.get("status"))||"".equals(a.get("terms")))return new Result(0,0);
        int scale=jdbc.queryForObject("SELECT fcr_decimal_places FROM fd_currency_ref WHERE fcr_currency_id=?",Integer.class,a.get("currency"));
        LocalDate start=(LocalDate)a.get("start"),last=jdbc.query("SELECT MAX(sia_business_date) FROM savings_interest_accrual WHERE sia_account_id=?",r->{java.sql.Date d=r.next()?r.getDate(1):null;return d==null?start:d.toLocalDate();},id);
        if(start.isAfter(LocalDate.MIN)&&last.isBefore(date)){
            JsonNode bands;try{bands=json.readTree((String)a.get("terms"));}catch(Exception e){throw new IllegalStateException("Stored savings interest terms are invalid",e);}
            BigDecimal balance=((BigDecimal)a.get("balance")).setScale(scale,RoundingMode.HALF_UP);LocalDate day=last.isBefore(start)?start.plusDays(1):last.plusDays(1);int rows=0;
            for(;!day.isAfter(date);day=day.plusDays(1)){BigDecimal rate=rate(bands,balance);if(rate.signum()==0)continue;BigDecimal interest=dailyInterest(balance,rate);int n=jdbc.update("INSERT IGNORE INTO savings_interest_accrual(sia_account_id,sia_business_date,sia_balance_basis,sia_rate,sia_interest,sia_created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",id,day,balance,rate,interest);rows+=n;}
            return new Result(rows,creditDue(id,date,quarterly,scale,(Long)a.get("currency"),start));
        }
        return new Result(0,creditDue(id,date,quarterly,scale,(Long)a.get("currency"),start));
    }

    private BigDecimal rate(JsonNode bands,BigDecimal balance){for(JsonNode b:bands){BigDecimal from=b.path("valueFrom").decimalValue(),to=b.path("valueTo").decimalValue();if(balance.compareTo(from)>=0&&balance.compareTo(to)<=0)return b.path("rate").decimalValue();}return BigDecimal.ZERO;}
    static BigDecimal dailyInterest(BigDecimal balance,BigDecimal rate){return balance.multiply(rate).divide(BigDecimal.valueOf(365),8,RoundingMode.HALF_UP);}
    private int creditDue(long id,LocalDate date,boolean quarterly,int scale,long currency,LocalDate opened){
        int credited=0;for(YearMonth period=YearMonth.from(opened);!period.isAfter(YearMonth.from(date));period=period.plusMonths(1)){
            if(quarterly&&period.getMonthValue()%3!=0)continue;LocalDate periodEnd=period.atEndOfMonth();if(periodEnd.isAfter(date))continue;LocalDate periodStart=quarterly?period.minusMonths(2).atDay(1):period.atDay(1);String ref="INTEREST-"+id+"-"+period.format(DateTimeFormatter.ofPattern("yyyyMM"));Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM savings_interest_accrual WHERE sia_account_id=? AND sia_business_date>=? AND sia_business_date<=? AND sia_credited_reference IS NULL",Integer.class,id,periodStart,periodEnd);if(count==null||count==0)continue;
            BigDecimal total=jdbc.queryForObject("SELECT COALESCE(SUM(sia_interest),0) FROM savings_interest_accrual WHERE sia_account_id=? AND sia_business_date>=? AND sia_business_date<=? AND sia_credited_reference IS NULL",BigDecimal.class,id,periodStart,periodEnd).setScale(scale,RoundingMode.HALF_UP);
            if(total.signum()>0){postings.postSystemCredit(id,currency,total,ref,"Interest credited for "+period.format(DateTimeFormatter.ofPattern("yyyy-MM")),"SYSTEM","SAVINGS_INTEREST:"+id+":"+period.format(DateTimeFormatter.ofPattern("yyyyMM")),"SAVINGS_INTEREST","INTEREST_EXPENSE","SAVINGS");credited++;}
            jdbc.update("UPDATE savings_interest_accrual SET sia_credited_reference=? WHERE sia_account_id=? AND sia_business_date>=? AND sia_business_date<=? AND sia_credited_reference IS NULL",ref,id,periodStart,periodEnd);
        }
        return credited;
    }
    public record Result(int accrualRowsCreated,int creditsPosted){}
}
