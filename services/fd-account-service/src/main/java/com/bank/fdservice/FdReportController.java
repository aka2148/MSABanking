package com.bank.fdservice;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class FdReportController {
    private final JdbcTemplate jdbc;
    private final DemoClockService clock;

    public FdReportController(JdbcTemplate jdbc, DemoClockService clock) { this.jdbc = jdbc; this.clock = clock; }

    @GetMapping("/portfolio")
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')")
    public PortfolioReport myPortfolio(Authentication actor) { return portfolio(actor.getName()); }

    @GetMapping("/portfolio/{username}")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public PortfolioReport customerPortfolio(@PathVariable String username) { return portfolio(username); }

    @GetMapping("/operational")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public OperationalReport operational() {
        LocalDate today = clock.businessDate();
        LocalDate target = today.plusDays(30);
        List<CurrencyTotals> totals = jdbc.query("SELECT c.fcr_iso_code,c.fcr_decimal_places,COUNT(*) AS active_count,SUM(a.fa_principal_amount) AS principal,SUM(COALESCE(x.accrued,0)) AS accrued,AVG(a.fa_interest_rate) AS avg_rate FROM fd_accounts a JOIN fd_currency_ref c ON c.fcr_currency_id=a.fa_currency_id LEFT JOIN (SELECT fia_account_id,SUM(fia_accrued_amount) accrued FROM fd_interest_accrual GROUP BY fia_account_id) x ON x.fia_account_id=a.fa_account_id WHERE a.fa_status='ACTIVE' GROUP BY c.fcr_iso_code,c.fcr_decimal_places ORDER BY c.fcr_iso_code",
                (rs, row) -> new CurrencyTotals(rs.getString(1), rs.getInt(2), rs.getLong(3), money(rs.getBigDecimal(4), rs.getInt(2)), money(rs.getBigDecimal(5), rs.getInt(2)), rs.getBigDecimal(6) == null ? BigDecimal.ZERO : rs.getBigDecimal(6).setScale(4, RoundingMode.HALF_UP)));
        List<DepositView> maturing = jdbc.query("SELECT a.fa_account_id,a.fa_account_number,a.fa_primary_holder_id,u.fur_username,a.fa_principal_amount,c.fcr_iso_code,c.fcr_decimal_places,a.fa_interest_rate,a.fa_open_date,a.fa_maturity_date,a.fa_maturity_amount,a.fa_status FROM fd_accounts a JOIN fd_currency_ref c ON c.fcr_currency_id=a.fa_currency_id LEFT JOIN fd_user_ref u ON u.fur_user_id=a.fa_primary_holder_id WHERE a.fa_status='ACTIVE' AND DATE(a.fa_maturity_date) BETWEEN ? AND ? ORDER BY a.fa_maturity_date,a.fa_account_id",
                (rs, row) -> new DepositView(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4), money(rs.getBigDecimal(5), rs.getInt(7)), rs.getString(6), rs.getBigDecimal(8), rs.getDate(9).toLocalDate(), rs.getDate(10).toLocalDate(), money(rs.getBigDecimal(11), rs.getInt(7)), rs.getString(12)), today, target);
        return new OperationalReport(totals, maturing);
    }

    private PortfolioReport portfolio(String username) {
        UserRef user = jdbc.query("SELECT fur_user_id,fur_full_name,fur_username FROM fd_user_ref WHERE fur_username=?", rs -> rs.next() ? new UserRef(rs.getLong(1), rs.getString(2), rs.getString(3)) : null, username);
        if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer portfolio not found");
        List<DepositView> accounts = jdbc.query("SELECT a.fa_account_id,a.fa_account_number,a.fa_primary_holder_id,u.fur_username,a.fa_principal_amount,c.fcr_iso_code,c.fcr_decimal_places,a.fa_interest_rate,a.fa_open_date,a.fa_maturity_date,a.fa_maturity_amount,a.fa_status FROM fd_accounts a JOIN fd_currency_ref c ON c.fcr_currency_id=a.fa_currency_id LEFT JOIN fd_user_ref u ON u.fur_user_id=a.fa_primary_holder_id WHERE a.fa_primary_holder_id=? ORDER BY a.fa_open_date DESC,a.fa_account_id DESC",
                (rs, row) -> new DepositView(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4), money(rs.getBigDecimal(5), rs.getInt(7)), rs.getString(6), rs.getBigDecimal(8), rs.getDate(9).toLocalDate(), rs.getDate(10).toLocalDate(), money(rs.getBigDecimal(11), rs.getInt(7)), rs.getString(12)), user.id());
        List<CurrencyTotals> totals = jdbc.query("SELECT c.fcr_iso_code,c.fcr_decimal_places,COUNT(*) AS active_count,SUM(a.fa_principal_amount) AS principal,SUM(COALESCE(x.accrued,0)) AS accrued,AVG(a.fa_interest_rate) AS avg_rate FROM fd_accounts a JOIN fd_currency_ref c ON c.fcr_currency_id=a.fa_currency_id LEFT JOIN (SELECT fia_account_id,SUM(fia_accrued_amount) accrued FROM fd_interest_accrual GROUP BY fia_account_id) x ON x.fia_account_id=a.fa_account_id WHERE a.fa_primary_holder_id=? AND a.fa_status='ACTIVE' GROUP BY c.fcr_iso_code,c.fcr_decimal_places ORDER BY c.fcr_iso_code",
                (rs, row) -> new CurrencyTotals(rs.getString(1), rs.getInt(2), rs.getLong(3), money(rs.getBigDecimal(4), rs.getInt(2)), money(rs.getBigDecimal(5), rs.getInt(2)), rs.getBigDecimal(6) == null ? BigDecimal.ZERO : rs.getBigDecimal(6).setScale(4, RoundingMode.HALF_UP)), user.id());
        return new PortfolioReport(user.username(), user.fullName(), totals, accounts);
    }

    private static BigDecimal money(BigDecimal amount, int scale) { return (amount == null ? BigDecimal.ZERO : amount).setScale(Math.max(0, scale), RoundingMode.HALF_UP); }

    private record UserRef(long id, String fullName, String username) {}
    public record CurrencyTotals(String currencyCode, int decimalPlaces, long activeCount, BigDecimal totalPrincipal, BigDecimal totalAccruedInterest, BigDecimal averageInterestRate) {}
    public record DepositView(long accountId, String accountNumber, long customerId, String username, BigDecimal principal, String currencyCode, BigDecimal annualRate, LocalDate openDate, LocalDate maturityDate, BigDecimal maturityAmount, String status) {}
    public record PortfolioReport(String customerUsername, String fullName, List<CurrencyTotals> currencies, List<DepositView> accounts) {}
    public record OperationalReport(List<CurrencyTotals> currencies, List<DepositView> maturingSoonAccounts) {}
}
