package com.bank.fdservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class SavingsInterestService {
    private final JdbcTemplate jdbc;private final SavingsInterestAccountProcessor processor;private final boolean quarterly;
    public SavingsInterestService(JdbcTemplate jdbc,SavingsInterestAccountProcessor processor,@Value("${app.savings.interest.credit-frequency:MONTHLY}") String frequency){this.jdbc=jdbc;this.processor=processor;if(!List.of("MONTHLY","QUARTERLY").contains(frequency.toUpperCase(java.util.Locale.ROOT)))throw new IllegalArgumentException("Unsupported savings interest credit frequency");this.quarterly="QUARTERLY".equalsIgnoreCase(frequency);}
    public BatchResult run(LocalDate date){List<Long> ids=jdbc.queryForList("SELECT sa_account_id FROM savings_accounts WHERE sa_account_type IN ('SAVINGS','CURRENT') AND sa_interest_terms IS NOT NULL AND sa_status<>'CLOSED' ORDER BY sa_account_id",Long.class);List<Long> failed=new ArrayList<>();int rows=0,credits=0,accounts=0;for(long id:ids){try{var result=processor.process(id,date,quarterly);rows+=result.accrualRowsCreated();credits+=result.creditsPosted();if(result.accrualRowsCreated()>0||result.creditsPosted()>0)accounts++;}catch(RuntimeException ex){failed.add(id);}}return new BatchResult(date,ids.size(),accounts,rows,credits,failed.size(),List.copyOf(failed));}
    public record BatchResult(LocalDate businessDate,int accountsExamined,int accountsProcessed,int accrualRowsCreated,int creditsPosted,int accountsFailed,List<Long> failedAccountIds){}
}
