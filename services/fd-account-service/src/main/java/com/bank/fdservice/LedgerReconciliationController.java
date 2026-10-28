package com.bank.fdservice;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/ledger/reconciliation")
@PreAuthorize("hasRole('ADMIN')")
public class LedgerReconciliationController {
    private final JdbcTemplate jdbc;
    public LedgerReconciliationController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    @Transactional(readOnly = true)
    public ReconciliationReport reconcile() {
        String cutover = jdbc.queryForObject("SELECT flc_cutover_at FROM fd_ledger_cutover WHERE flc_singleton_id=1", java.sql.Timestamp.class).toInstant().toString();
        List<HeaderMismatch> headers = jdbc.query(
                "SELECT j.fj_id,j.fj_key,IF(j.fj_posted_at < c.flc_cutover_at,'HISTORICAL','CURRENT') period,j.fj_total_debit,j.fj_total_credit,COALESCE(SUM(l.jl_debit),0) line_debit,COALESCE(SUM(l.jl_credit),0) line_credit FROM fd_journals j CROSS JOIN fd_ledger_cutover c LEFT JOIN fd_journal_lines l ON l.jl_journal_id=j.fj_id WHERE c.flc_singleton_id=1 GROUP BY j.fj_id,j.fj_key,period,j.fj_total_debit,j.fj_total_credit HAVING j.fj_total_debit<>COALESCE(SUM(l.jl_debit),0) OR j.fj_total_credit<>COALESCE(SUM(l.jl_credit),0)",
                (rs,n)->new HeaderMismatch(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getBigDecimal(4),rs.getBigDecimal(5),rs.getBigDecimal(6),rs.getBigDecimal(7)));
        List<CurrencyBalance> currencies = jdbc.query(
                "SELECT c.fcr_iso_code,IF(j.fj_posted_at < cut.flc_cutover_at,'HISTORICAL','CURRENT') period,SUM(l.jl_debit) debit_total,SUM(l.jl_credit) credit_total,SUM(l.jl_debit)-SUM(l.jl_credit) difference FROM fd_journal_lines l JOIN fd_journals j ON j.fj_id=l.jl_journal_id JOIN fd_currency_ref c ON c.fcr_currency_id=j.fj_currency_id CROSS JOIN fd_ledger_cutover cut WHERE cut.flc_singleton_id=1 GROUP BY c.fcr_iso_code,period ORDER BY c.fcr_iso_code,period",
                (rs,n)->new CurrencyBalance(rs.getString(1),rs.getString(2),rs.getBigDecimal(3),rs.getBigDecimal(4),rs.getBigDecimal(5)));
        List<AccountBalance> accounts = jdbc.query(
                "SELECT s.sa_account_id,c.fcr_iso_code,s.sa_balance,COALESCE(h.current_net,0),COALESCE(h.historical_net,0),COALESCE(j.current_net,0),COALESCE(j.historical_net,0),COALESCE(h.current_count,0),(SELECT t2.st_balance_after FROM savings_transactions t2 WHERE t2.st_account_id=s.sa_account_id AND t2.st_posted_at>=cut.flc_cutover_at ORDER BY t2.st_posted_at DESC,t2.st_id DESC LIMIT 1) FROM savings_accounts s JOIN fd_currency_ref c ON c.fcr_currency_id=s.sa_currency_id CROSS JOIN fd_ledger_cutover cut LEFT JOIN (SELECT t.st_account_id,SUM(CASE WHEN t.st_posted_at>=cut2.flc_cutover_at THEN IF(t.st_entry_type='CREDIT',t.st_amount,-t.st_amount) ELSE 0 END) current_net,SUM(CASE WHEN t.st_posted_at<cut2.flc_cutover_at THEN IF(t.st_entry_type='CREDIT',t.st_amount,-t.st_amount) ELSE 0 END) historical_net,SUM(CASE WHEN t.st_posted_at>=cut2.flc_cutover_at THEN 1 ELSE 0 END) current_count FROM savings_transactions t CROSS JOIN fd_ledger_cutover cut2 WHERE cut2.flc_singleton_id=1 GROUP BY t.st_account_id) h ON h.st_account_id=s.sa_account_id LEFT JOIN (SELECT COALESCE(l.jl_savings_account_id,jj.fj_savings_account_id) account_id,jj.fj_currency_id,SUM(CASE WHEN jj.fj_posted_at>=cut3.flc_cutover_at THEN IF(l.jl_credit>0,l.jl_credit,-l.jl_debit) ELSE 0 END) current_net,SUM(CASE WHEN jj.fj_posted_at<cut3.flc_cutover_at THEN IF(l.jl_credit>0,l.jl_credit,-l.jl_debit) ELSE 0 END) historical_net FROM fd_journal_lines l JOIN fd_journals jj ON jj.fj_id=l.jl_journal_id CROSS JOIN fd_ledger_cutover cut3 WHERE cut3.flc_singleton_id=1 AND l.jl_account_code IN ('SAVINGS','CURRENT_ACCOUNT') GROUP BY account_id,jj.fj_currency_id) j ON j.account_id=s.sa_account_id AND j.fj_currency_id=s.sa_currency_id WHERE cut.flc_singleton_id=1 ORDER BY s.sa_account_id",
                (rs,n)->{
                    BigDecimal balance=rs.getBigDecimal(3),hc=rs.getBigDecimal(4),hh=rs.getBigDecimal(5),jc=rs.getBigDecimal(6),jh=rs.getBigDecimal(7);
                    boolean currentHistory=rs.getLong(8)>0;
                    BigDecimal latest=rs.getBigDecimal(9);
                    boolean balanceMismatch=currentHistory&&latest!=null&&balance.compareTo(latest)!=0;
                    return new AccountBalance(rs.getLong(1),rs.getString(2),balance,hc,hh,jc,jh,hc.compareTo(jc)!=0, balanceMismatch);
                });
        List<HistorySequenceMismatch> sequence = jdbc.query(
                "SELECT account_id,transaction_id,reference_id,period,expected_balance,actual_balance FROM (SELECT t.st_account_id account_id,t.st_id transaction_id,t.st_reference_id reference_id,IF(t.st_posted_at<cut.flc_cutover_at,'HISTORICAL','CURRENT') period,t.st_balance_after actual_balance,LAG(t.st_balance_after) OVER(PARTITION BY t.st_account_id ORDER BY t.st_posted_at,t.st_id)+IF(t.st_entry_type='CREDIT',t.st_amount,-t.st_amount) expected_balance FROM savings_transactions t CROSS JOIN fd_ledger_cutover cut WHERE cut.flc_singleton_id=1) q WHERE expected_balance IS NOT NULL AND expected_balance<>actual_balance",
                (rs,n)->new HistorySequenceMismatch(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getBigDecimal(5),rs.getBigDecimal(6)));
        return new ReconciliationReport(cutover,headers,currencies,accounts,sequence);
    }

    public record ReconciliationReport(String cutoverAt,List<HeaderMismatch> journalHeaderMismatches,List<CurrencyBalance> currencyBalances,List<AccountBalance> savingsAccounts,List<HistorySequenceMismatch> historySequenceMismatches) {}
    public record HeaderMismatch(long journalId,String reference,String period,BigDecimal headerDebit,BigDecimal headerCredit,BigDecimal linesDebit,BigDecimal linesCredit) {}
    public record CurrencyBalance(String currency,String period,BigDecimal debitTotal,BigDecimal creditTotal,BigDecimal difference) {}
    public record AccountBalance(long accountId,String currency,BigDecimal balance,BigDecimal currentHistoryNet,BigDecimal historicalHistoryNet,BigDecimal currentJournalNet,BigDecimal historicalJournalNet,boolean currentHistoryJournalMismatch,boolean currentBalanceHistoryMismatch) {}
    public record HistorySequenceMismatch(long accountId,long transactionId,String reference,String period,BigDecimal expectedBalance,BigDecimal actualBalance) {}
}
