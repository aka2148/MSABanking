package com.bank.fdservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class FdBatchService {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final DemoClockService clock;
    public FdBatchService(JdbcTemplate jdbc,ObjectMapper json,DemoClockService clock){this.jdbc=jdbc;this.json=json;this.clock=clock;}

    public DailyBatchResult runDaily(LocalDate requestedDate){
        LocalDate date=requestedDate==null?clock.businessDate():requestedDate;
        int active=jdbc.queryForObject("SELECT COUNT(*) FROM fd_accounts WHERE fa_status='ACTIVE'",Integer.class);
        BatchResult accrual=accrue(date);
        BatchResult maturity=mature(date);
        return new DailyBatchResult(date,active,accrual.accountsProcessed(),maturity.accountsProcessed(),0,List.of());
    }

    @Transactional
    public BatchResult accrue(LocalDate requestedDate){
        LocalDate date=requestedDate==null?clock.businessDate():requestedDate;
        List<Long> ids=jdbc.queryForList("SELECT fa_account_id FROM fd_accounts WHERE fa_status='ACTIVE' ORDER BY fa_account_id",Long.class);
        int accounts=0,rows=0;
        for(Long id:ids){int added=accrueOne(id,date);if(added>0){accounts++;rows+=added;}}
        return new BatchResult(date,accounts,rows);
    }

    @Transactional
    public BatchResult mature(LocalDate requestedDate){
        LocalDate date=requestedDate==null?clock.businessDate():requestedDate;
        List<Long> ids=jdbc.queryForList("SELECT fa_account_id FROM fd_accounts WHERE fa_status='ACTIVE' AND DATE(fa_maturity_date)<=? ORDER BY fa_account_id",Long.class,java.sql.Date.valueOf(date));
        int count=0;
        for(Long id:ids){
            Map<String,Object> fd=lockAccount(id);
            if(!"ACTIVE".equals(fd.get("status")))continue;
            LocalDate maturity=((java.sql.Date)fd.get("maturity")).toLocalDate();
            if(maturity.isAfter(date))continue;
            accrueOne(id,maturity);
            BigDecimal principal=(BigDecimal)fd.get("principal");long currency=((Number)fd.get("currency")).longValue();long savings=((Number)fd.get("savings")).longValue();
            Integer scale=jdbc.queryForObject("SELECT fcr_decimal_places FROM fd_currency_ref WHERE fcr_currency_id=?",Integer.class,currency);int decimals=scale==null?2:scale;
            BigDecimal balance=lastCompoundingBalance(id,principal).setScale(decimals,RoundingMode.HALF_UP);
            BigDecimal interest=balance.subtract(principal).max(BigDecimal.ZERO).setScale(decimals,RoundingMode.HALF_UP);
            jdbc.update("UPDATE fd_accounts SET fa_status='MATURED',fa_maturity_amount=?,fa_updated_by='fd-batch',fa_updated_date=UTC_TIMESTAMP(6),fa_row_version=fa_row_version+1 WHERE fa_account_id=? AND fa_status='ACTIVE'",balance,id);
            if(interest.signum()>0){
                String key="INTEREST:"+id+":MATURITY";
                if(!exists("SELECT fj_id FROM fd_journals WHERE fj_key=?",key)){
                    long journal=journal(key,id,savings,currency,"INTEREST",interest,null);
                    lines(journal,"INTEREST_EXPENSE",interest,"FIXED_DEPOSIT",interest);
                }
            }
            jdbc.update("INSERT INTO fd_transactions(ft_account_id,ft_txn_type_id,ft_amount,ft_balance_after,ft_currency_id,ft_remarks,ft_created_by) VALUES(?,?,?,?,?,?,?)",id,typeId("FD_MATURITY"),balance,balance,currency,"Internal maturity record; no external payment rail was called","fd-batch");
            event(id,"MATURED",Map.of("status","MATURED","maturityAmount",balance,"maturityDate",maturity.toString()));count++;
        }
        return new BatchResult(date,count,0);
    }

    private int accrueOne(long id,LocalDate requestedEnd){
        Map<String,Object> fd=lockAccount(id);
        if(!"ACTIVE".equals(fd.get("status")))return 0;
        LocalDate start=((java.sql.Timestamp)fd.get("opened")).toLocalDateTime().toLocalDate();
        LocalDate maturity=((java.sql.Date)fd.get("maturity")).toLocalDate();
        LocalDate end=requestedEnd.isAfter(maturity)?maturity:requestedEnd;
        if(end.isBefore(start))return 0;
        BigDecimal principal=(BigDecimal)fd.get("principal"),rate=(BigDecimal)fd.get("rate");long currency=((Number)fd.get("currency")).longValue();
        Integer scaleValue=jdbc.queryForObject("SELECT fcr_decimal_places FROM fd_currency_ref WHERE fcr_currency_id=?",Integer.class,currency);int scale=scaleValue==null?2:scaleValue;
        List<Map<String,Object>> old=jdbc.queryForList("SELECT fia_accrual_date,fia_accrued_amount,fia_cumulative_accrued,fia_compounding_balance,fia_capitalized_amount FROM fd_interest_accrual WHERE fia_account_id=? ORDER BY fia_accrual_date,fia_accrual_id",id);
        LocalDate last=start;BigDecimal balance=principal,cumulative=BigDecimal.ZERO.setScale(8,RoundingMode.HALF_UP),pending=BigDecimal.ZERO.setScale(8,RoundingMode.HALF_UP);
        for(Map<String,Object> row:old){
            LocalDate d=localDate(row.get("fia_accrual_date"));
            last=d;BigDecimal accrued=(BigDecimal)row.get("fia_accrued_amount");
            cumulative=row.get("fia_cumulative_accrued")==null?cumulative.add(accrued):(BigDecimal)row.get("fia_cumulative_accrued");
            if(row.get("fia_compounding_balance")!=null)balance=(BigDecimal)row.get("fia_compounding_balance");
            BigDecimal capitalized=(BigDecimal)row.get("fia_capitalized_amount");
            pending=capitalized!=null&&capitalized.signum()>0?BigDecimal.ZERO.setScale(8,RoundingMode.HALF_UP):pending.add(accrued);
        }
        if(!last.isBefore(end))return 0;
        int created=0;
        for(LocalDate d=last.plusDays(1);!d.isAfter(end);d=d.plusDays(1)){
            BigDecimal daily=balance.multiply(rate).divide(BigDecimal.valueOf(365),12,RoundingMode.HALF_UP).setScale(8,RoundingMode.HALF_UP);
            cumulative=cumulative.add(daily);pending=pending.add(daily);BigDecimal capitalized=BigDecimal.ZERO.setScale(8,RoundingMode.HALF_UP);
            if(FdCompounding.capitalizationDate(start,d,(String)fd.get("compounding"))||d.equals(maturity)){
                capitalized=pending.setScale(scale,RoundingMode.HALF_UP).setScale(8,RoundingMode.HALF_UP);balance=balance.add(capitalized);pending=BigDecimal.ZERO.setScale(8,RoundingMode.HALF_UP);
            }
            jdbc.update("INSERT INTO fd_interest_accrual(fia_account_id,fia_accrual_date,fia_accrued_amount,fia_cumulative_accrued,fia_currency_id,fia_compounding_balance,fia_capitalized_amount) VALUES(?,?,?,?,?,?,?)",id,java.sql.Timestamp.valueOf(d.atStartOfDay()),daily,cumulative,currency,balance,capitalized);created++;
        }
        jdbc.update("UPDATE fd_accounts SET fa_maturity_amount=?,fa_updated_by='fd-batch',fa_updated_date=UTC_TIMESTAMP(6) WHERE fa_account_id=?",balance.add(pending).setScale(scale,RoundingMode.HALF_UP),id);
        event(id,"INTEREST_ACCRUED",Map.of("throughDate",end.toString(),"cumulativeInterest",cumulative.setScale(scale,RoundingMode.HALF_UP),"newAccrualDays",created));
        return created;
    }

    private Map<String,Object> lockAccount(long id){return jdbc.query("SELECT fa_primary_holder_id,fa_principal_amount,fa_currency_id,fa_status,fa_funding_savings_account_id,fa_open_date,fa_maturity_date,fa_interest_rate,fa_compounding_frequency FROM fd_accounts WHERE fa_account_id=? FOR UPDATE",r->r.next()?Map.of("owner",r.getLong(1),"principal",r.getBigDecimal(2),"currency",r.getLong(3),"status",r.getString(4),"savings",r.getLong(5),"opened",r.getTimestamp(6),"maturity",r.getDate(7),"rate",r.getBigDecimal(8),"compounding",FdCompounding.basis(r.getString(9))):null,id);}
    private BigDecimal lastCompoundingBalance(long id,BigDecimal principal){return jdbc.query("SELECT fia_compounding_balance FROM fd_interest_accrual WHERE fia_account_id=? ORDER BY fia_accrual_date DESC,fia_accrual_id DESC LIMIT 1",r->r.next()&&r.getBigDecimal(1)!=null?r.getBigDecimal(1):principal,id);}
    private long typeId(String code){return jdbc.queryForObject("SELECT tt_txn_type_id FROM transaction_types WHERE tt_txn_type_code=?",Long.class,code);}
    private boolean exists(String sql,Object arg){return Boolean.TRUE.equals(jdbc.query(sql,(org.springframework.jdbc.core.ResultSetExtractor<Boolean>)r->r.next(),arg));}
    private LocalDate localDate(Object value){if(value instanceof java.sql.Timestamp timestamp)return timestamp.toLocalDateTime().toLocalDate();if(value instanceof java.time.LocalDateTime dateTime)return dateTime.toLocalDate();if(value instanceof java.sql.Date date)return date.toLocalDate();if(value instanceof LocalDate date)return date;throw new IllegalStateException("Unexpected MySQL date value: "+(value==null?"null":value.getClass().getName()));}
    private long journal(String key,long fd,long savings,long currency,String operation,BigDecimal amount,BigDecimal savingsBalance){jdbc.update("INSERT INTO fd_journals(fj_key,fj_fd_account_id,fj_savings_account_id,fj_currency_id,fj_operation,fj_posted_at,fj_total_debit,fj_total_credit,fj_savings_balance_after) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6),?,?,?)",key,fd,savings,currency,operation,amount,amount,savingsBalance);return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}
    private void lines(long id,String debitCode,BigDecimal debit,String creditCode,BigDecimal credit){jdbc.update("INSERT INTO fd_journal_lines(jl_journal_id,jl_sequence,jl_account_code,jl_debit,jl_credit) VALUES(?,1,?,?,0),(?,2,?,0,?)",id,debitCode,debit,id,creditCode,credit);}
    private void event(long id,String type,Object body){try{jdbc.update("INSERT INTO fd_account_events(fae_account_id,fae_event_type,fae_payload,fae_aggregate_version,fae_created_by) VALUES(?,?,CAST(? AS JSON),COALESCE((SELECT fa_row_version FROM fd_accounts WHERE fa_account_id=?),0),'fd-batch')",id,type,json.writeValueAsString(body),id);}catch(Exception e){throw new IllegalStateException("Could not record FD event",e);}}
    public record BatchResult(LocalDate businessDate,int accountsProcessed,int accrualRowsCreated){}
    public record DailyBatchResult(@com.fasterxml.jackson.annotation.JsonProperty("business_date") LocalDate businessDate,
                                   @com.fasterxml.jackson.annotation.JsonProperty("accounts_examined") int accountsExamined,
                                   @com.fasterxml.jackson.annotation.JsonProperty("accruals_processed") int accrualsProcessed,
                                   @com.fasterxml.jackson.annotation.JsonProperty("maturities_processed") int maturitiesProcessed,
                                   @com.fasterxml.jackson.annotation.JsonProperty("accounts_failed") int accountsFailed,
                                   @com.fasterxml.jackson.annotation.JsonProperty("failed_account_ids") List<Long> failedAccountIds) {}
}
