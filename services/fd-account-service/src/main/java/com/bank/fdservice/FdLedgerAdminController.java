package com.bank.fdservice;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/ledger")
@PreAuthorize("hasRole('ADMIN')")
public class FdLedgerAdminController {
    private final JdbcTemplate jdbc;

    public FdLedgerAdminController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** With no account filter, returns the branch-wide journal for this FD service. */
    @GetMapping
    @Transactional(readOnly = true)
    public LedgerPage entries(@RequestParam(name = "accountId", required = false) Long accountId,
                              @RequestParam(name = "fixedDepositId", required = false) Long fixedDepositId,
                              @RequestParam(name = "page", defaultValue = "0") int page,
                              @RequestParam(name = "pageSize", defaultValue = "25") int pageSize) {
        if (accountId != null && fixedDepositId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose either a savings account or a fixed deposit filter");
        }
        if ((accountId != null && accountId <= 0) || (fixedDepositId != null && fixedDepositId <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account IDs must be positive");
        }

        int safePage = Math.max(0, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        String where = accountId != null ? " WHERE j.fj_savings_account_id=?"
                : fixedDepositId != null ? " WHERE j.fj_fd_account_id=?" : "";
        Object[] filterArgs = accountId != null ? new Object[]{accountId}
                : fixedDepositId != null ? new Object[]{fixedDepositId} : new Object[]{};
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals j" + where, Long.class, filterArgs);
        long offset = (long) safePage * safePageSize;
        List<LedgerEntry> rows = jdbc.query(
                "SELECT j.fj_id,j.fj_fd_account_id,a.fa_account_number,j.fj_savings_account_id,s.sa_account_number," +
                        "j.fj_operation,j.fj_posted_at,c.fcr_iso_code,j.fj_total_debit,j.fj_total_credit,j.fj_savings_balance_after " +
                        "FROM fd_journals j LEFT JOIN fd_accounts a ON a.fa_account_id=j.fj_fd_account_id " +
                        "JOIN savings_accounts s ON s.sa_account_id=j.fj_savings_account_id " +
                        "JOIN fd_currency_ref c ON c.fcr_currency_id=j.fj_currency_id" + where +
                        " ORDER BY j.fj_posted_at DESC,j.fj_id DESC LIMIT ? OFFSET ?",
                (rs, row) -> {
                    long journalId = rs.getLong(1);
                    Object fdIdValue = rs.getObject(2);
                    Long fdId = fdIdValue == null ? null : ((Number) fdIdValue).longValue();
                    return new LedgerEntry(journalId, rs.getString(6), rs.getTimestamp(7).toInstant().toString(),
                            rs.getLong(4), mask(rs.getString(5)), fdId, mask(rs.getString(3)), rs.getString(8),
                            rs.getBigDecimal(9), rs.getBigDecimal(10), rs.getBigDecimal(11),
                            jdbc.query("SELECT jl_account_code,jl_debit,jl_credit FROM fd_journal_lines " +
                                            "WHERE jl_journal_id=? ORDER BY jl_sequence",
                                    (line, lineNumber) -> new JournalLine(line.getString(1), line.getBigDecimal(2), line.getBigDecimal(3)),
                                    journalId));
                }, append(filterArgs, safePageSize, offset));
        return new LedgerPage(rows, safePage, safePageSize, total);
    }

    private static Object[] append(Object[] values, Object... tail) {
        Object[] all = Arrays.copyOf(values, values.length + tail.length);
        System.arraycopy(tail, 0, all, values.length, tail.length);
        return all;
    }

    private static String mask(String value) {
        if (value == null) return null;
        return value.length() <= 4 ? "••••" : "••••" + value.substring(value.length() - 4);
    }

    public record LedgerPage(List<LedgerEntry> content, int number, int size, long totalElements) {}

    public record LedgerEntry(long journalId, String operation, String postedAt,
                              long savingsAccountId, String savingsAccountNumber,
                              Long fixedDepositId, String fixedDepositAccountNumber,
                              String currencyCode, BigDecimal totalDebit, BigDecimal totalCredit,
                              BigDecimal savingsBalanceAfter, List<JournalLine> lines) {}

    public record JournalLine(String accountCode, BigDecimal debit, BigDecimal credit) {}
}
