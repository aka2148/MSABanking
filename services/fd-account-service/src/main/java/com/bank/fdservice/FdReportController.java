package com.bank.fdservice;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class FdReportController {
    private final JdbcTemplate jdbc;
    private final DemoClockService clock;

    public FdReportController(JdbcTemplate jdbc, DemoClockService clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @GetMapping({"/portfolio", "/customer-portfolio"})
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')")
    public PortfolioReport myPortfolio(Authentication actor) {
        return portfolio(actor.getName());
    }

    @GetMapping({"/portfolio/{username}", "/customer-portfolio/{username}"})
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public PortfolioReport customerPortfolio(@PathVariable String username) {
        return portfolio(username);
    }

    @GetMapping(path = {"/portfolio.csv", "/customer-portfolio.csv"}, produces = "text/csv")
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')")
    public ResponseEntity<byte[]> myPortfolioCsv(Authentication actor) {
        return portfolioCsv(portfolio(actor.getName()), "customer-portfolio.csv");
    }

    @GetMapping(path = {"/portfolio/{username}.csv", "/customer-portfolio/{username}.csv"}, produces = "text/csv")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public ResponseEntity<byte[]> customerPortfolioCsv(@PathVariable String username) {
        return portfolioCsv(portfolio(username), "customer-portfolio.csv");
    }

    @GetMapping("/fd-summary")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public List<FdSummaryRow> fdSummary() {
        return jdbc.query("SELECT p.fpr_product_code,p.fpr_product_name,c.fcr_iso_code,c.fcr_decimal_places,a.fa_status,COUNT(*)," +
                        "SUM(a.fa_principal_amount),SUM(COALESCE(ac.accrued,0)),SUM(COALESCE(a.fa_maturity_amount,0)) " +
                        "FROM fd_accounts a JOIN fd_product_ref p ON p.fpr_product_id=a.fa_product_id " +
                        "JOIN fd_currency_ref c ON c.fcr_currency_id=a.fa_currency_id " +
                        "LEFT JOIN (SELECT fia_account_id,SUM(fia_accrued_amount) accrued FROM fd_interest_accrual GROUP BY fia_account_id) ac " +
                        "ON ac.fia_account_id=a.fa_account_id " +
                        "GROUP BY p.fpr_product_code,p.fpr_product_name,c.fcr_iso_code,c.fcr_decimal_places,a.fa_status " +
                        "ORDER BY p.fpr_product_code,c.fcr_iso_code,a.fa_status",
                (rs, row) -> {
                    int scale = rs.getInt(4);
                    return new FdSummaryRow(rs.getString(1), rs.getString(2), rs.getString(3), scale, rs.getString(5),
                            rs.getLong(6), money(rs.getBigDecimal(7), scale), money(rs.getBigDecimal(8), scale), money(rs.getBigDecimal(9), scale));
                });
    }

    @GetMapping(value = "/fd-summary.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public ResponseEntity<byte[]> fdSummaryCsv() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"productCode", "productName", "currencyCode", "decimalPlaces", "status", "totalAccounts", "totalPrincipal", "totalAccruedInterest", "storedMaturityAmountTotal"});
        for (FdSummaryRow row : fdSummary()) rows.add(new String[]{row.productCode(), row.productName(), row.currencyCode(),
                Integer.toString(row.decimalPlaces()), row.status(), Long.toString(row.totalAccounts()), decimal(row.totalPrincipal()),
                decimal(row.totalAccruedInterest()), decimal(row.storedMaturityAmountTotal())});
        return csv("fd-summary.csv", rows);
    }

    @GetMapping("/maturity-summary")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public List<MaturitySummaryRow> maturitySummary() {
        LocalDate from = clock.businessDate();
        LocalDate through = from.plusDays(30);
        return jdbc.query("SELECT DATE(a.fa_maturity_date),p.fpr_product_code,p.fpr_product_name,c.fcr_iso_code,c.fcr_decimal_places,COUNT(*)," +
                        "SUM(a.fa_principal_amount),SUM(COALESCE(ac.accrued,0)),SUM(COALESCE(a.fa_maturity_amount,0)) " +
                        "FROM fd_accounts a JOIN fd_product_ref p ON p.fpr_product_id=a.fa_product_id " +
                        "JOIN fd_currency_ref c ON c.fcr_currency_id=a.fa_currency_id " +
                        "LEFT JOIN (SELECT fia_account_id,SUM(fia_accrued_amount) accrued FROM fd_interest_accrual GROUP BY fia_account_id) ac " +
                        "ON ac.fia_account_id=a.fa_account_id " +
                        "WHERE a.fa_status='ACTIVE' AND DATE(a.fa_maturity_date) BETWEEN ? AND ? " +
                        "GROUP BY DATE(a.fa_maturity_date),p.fpr_product_code,p.fpr_product_name,c.fcr_iso_code,c.fcr_decimal_places " +
                        "ORDER BY DATE(a.fa_maturity_date),p.fpr_product_code,c.fcr_iso_code",
                (rs, row) -> {
                    int scale = rs.getInt(5);
                    return new MaturitySummaryRow(rs.getDate(1).toLocalDate(), rs.getString(2), rs.getString(3), rs.getString(4), scale,
                            rs.getLong(6), money(rs.getBigDecimal(7), scale), money(rs.getBigDecimal(8), scale), money(rs.getBigDecimal(9), scale));
                }, from, through);
    }

    @GetMapping(value = "/maturity-summary.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public ResponseEntity<byte[]> maturitySummaryCsv() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"maturityDate", "productCode", "productName", "currencyCode", "decimalPlaces", "accountsDue", "totalPrincipal", "totalAccruedInterest", "storedMaturityAmountTotal"});
        for (MaturitySummaryRow row : maturitySummary()) rows.add(new String[]{row.maturityDate().toString(), row.productCode(), row.productName(),
                row.currencyCode(), Integer.toString(row.decimalPlaces()), Long.toString(row.accountsDue()), decimal(row.totalPrincipal()),
                decimal(row.totalAccruedInterest()), decimal(row.storedMaturityAmountTotal())});
        return csv("maturity-summary.csv", rows);
    }

    @GetMapping("/interest-history")
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')")
    public List<InterestHistoryRow> myInterestHistory(Authentication actor) {
        return interestHistoryFor(actor.getName());
    }

    @GetMapping("/interest-history/customer/{username}")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public List<InterestHistoryRow> customerInterestHistory(@PathVariable String username) {
        return interestHistoryFor(username);
    }

    @GetMapping(value = "/interest-history.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')")
    public ResponseEntity<byte[]> myInterestHistoryCsv(Authentication actor) {
        return interestHistoryCsv(interestHistoryFor(actor.getName()), "interest-history.csv");
    }

    @GetMapping(value = "/interest-history/customer/{username}.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public ResponseEntity<byte[]> customerInterestHistoryCsv(@PathVariable String username) {
        return interestHistoryCsv(interestHistoryFor(username), "interest-history.csv");
    }

    @GetMapping("/statements")
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')")
    public List<StatementRecord> myStatements(Authentication actor) {
        return statementsFor(actor.getName());
    }

    @GetMapping("/statements/customer/{username}")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public List<StatementRecord> customerStatements(@PathVariable String username) {
        return statementsFor(username);
    }

    @GetMapping(value = "/statements.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')")
    public ResponseEntity<byte[]> myStatementsCsv(Authentication actor) {
        return statementCsv(statementsFor(actor.getName()), "fd-statements.csv");
    }

    @GetMapping(value = "/statements/customer/{username}.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public ResponseEntity<byte[]> customerStatementsCsv(@PathVariable String username) {
        return statementCsv(statementsFor(username), "fd-statements.csv");
    }

    @GetMapping("/notices")
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')")
    public List<NoticeRecord> myNotices(Authentication actor) {
        return noticesFor(actor.getName());
    }

    @GetMapping("/notices/customer/{username}")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public List<NoticeRecord> customerNotices(@PathVariable String username) {
        return noticesFor(username);
    }

    @GetMapping(value = "/notices.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')")
    public ResponseEntity<byte[]> myNoticesCsv(Authentication actor) {
        return noticeCsv(noticesFor(actor.getName()), "fd-notices.csv");
    }

    @GetMapping(value = "/notices/customer/{username}.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public ResponseEntity<byte[]> customerNoticesCsv(@PathVariable String username) {
        return noticeCsv(noticesFor(username), "fd-notices.csv");
    }

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

    @GetMapping(value = "/operational.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public ResponseEntity<byte[]> operationalCsv() {
        OperationalReport report = operational();
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"recordType", "currencyCode", "decimalPlaces", "accountCount", "principal", "accruedInterest", "averageRate", "accountId", "accountNumber", "maturityDate", "status"});
        for (CurrencyTotals row : report.currencies()) rows.add(new String[]{"currencyTotals", row.currencyCode(), Integer.toString(row.decimalPlaces()),
                Long.toString(row.activeCount()), decimal(row.totalPrincipal()), decimal(row.totalAccruedInterest()), decimal(row.averageInterestRate()), "", "", "", ""});
        for (DepositView row : report.maturingSoonAccounts()) rows.add(new String[]{"maturingAccount", row.currencyCode(), "", "1", decimal(row.principal()), "", "",
                Long.toString(row.accountId()), row.accountNumber(), row.maturityDate().toString(), row.status()});
        return csv("fd-operational.csv", rows);
    }

    private PortfolioReport portfolio(String username) {
        UserRef user = user(username);
        List<DepositView> accounts = jdbc.query("SELECT a.fa_account_id,a.fa_account_number,a.fa_primary_holder_id,u.fur_username,a.fa_principal_amount,c.fcr_iso_code,c.fcr_decimal_places,a.fa_interest_rate,a.fa_open_date,a.fa_maturity_date,a.fa_maturity_amount,a.fa_status FROM fd_accounts a JOIN fd_currency_ref c ON c.fcr_currency_id=a.fa_currency_id LEFT JOIN fd_user_ref u ON u.fur_user_id=a.fa_primary_holder_id WHERE a.fa_primary_holder_id=? ORDER BY a.fa_open_date DESC,a.fa_account_id DESC",
                (rs, row) -> deposit(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6), rs.getInt(7), rs.getBigDecimal(8), rs.getDate(9), rs.getDate(10), rs.getBigDecimal(11), rs.getString(12)), user.id());
        List<CurrencyTotals> totals = jdbc.query("SELECT c.fcr_iso_code,c.fcr_decimal_places,COUNT(*) AS active_count,SUM(a.fa_principal_amount) AS principal,SUM(COALESCE(x.accrued,0)) AS accrued,AVG(a.fa_interest_rate) AS avg_rate FROM fd_accounts a JOIN fd_currency_ref c ON c.fcr_currency_id=a.fa_currency_id LEFT JOIN (SELECT fia_account_id,SUM(fia_accrued_amount) accrued FROM fd_interest_accrual GROUP BY fia_account_id) x ON x.fia_account_id=a.fa_account_id WHERE a.fa_primary_holder_id=? AND a.fa_status='ACTIVE' GROUP BY c.fcr_iso_code,c.fcr_decimal_places ORDER BY c.fcr_iso_code",
                (rs, row) -> new CurrencyTotals(rs.getString(1), rs.getInt(2), rs.getLong(3), money(rs.getBigDecimal(4), rs.getInt(2)), money(rs.getBigDecimal(5), rs.getInt(2)), rs.getBigDecimal(6) == null ? BigDecimal.ZERO : rs.getBigDecimal(6).setScale(4, RoundingMode.HALF_UP)), user.id());
        return new PortfolioReport(user.username(), user.fullName(), totals, accounts);
    }

    private List<InterestHistoryRow> interestHistoryFor(String username) {
        long userId = user(username).id();
        return jdbc.query("SELECT a.fa_account_id,a.fa_account_number,DATE(i.fia_accrual_date),i.fia_accrued_amount,i.fia_cumulative_accrued,i.fia_capitalized_amount,c.fcr_iso_code,c.fcr_decimal_places " +
                        "FROM fd_interest_accrual i JOIN fd_accounts a ON a.fa_account_id=i.fia_account_id JOIN fd_currency_ref c ON c.fcr_currency_id=a.fa_currency_id " +
                        "WHERE a.fa_primary_holder_id=? ORDER BY i.fia_accrual_date,a.fa_account_id",
                (rs, row) -> {
                    int scale = rs.getInt(8);
                    return new InterestHistoryRow(rs.getLong(1), rs.getString(2), rs.getDate(3).toLocalDate(), money(rs.getBigDecimal(4), scale),
                            money(rs.getBigDecimal(5), scale), money(rs.getBigDecimal(6), scale), rs.getString(7), scale);
                }, userId);
    }

    private List<StatementRecord> statementsFor(String username) {
        long userId = user(username).id();
        return jdbc.query("SELECT s.fs_statement_id,a.fa_account_id,a.fa_account_number,c.fcr_iso_code,c.fcr_decimal_places,s.fs_period_start,s.fs_period_end,s.fs_generated_date,a.fa_principal_amount,a.fa_status, " +
                        "COALESCE((SELECT SUM(i.fia_accrued_amount) FROM fd_interest_accrual i WHERE i.fia_account_id=a.fa_account_id AND i.fia_accrual_date>=s.fs_period_start AND i.fia_accrual_date<s.fs_period_end),0) " +
                        "FROM fd_statements s JOIN fd_accounts a ON a.fa_account_id=s.fs_account_id JOIN fd_currency_ref c ON c.fcr_currency_id=a.fa_currency_id " +
                        "WHERE a.fa_primary_holder_id=? ORDER BY s.fs_period_end DESC,s.fs_statement_id DESC",
                (rs, row) -> {
                    int scale = rs.getInt(5);
                    return new StatementRecord(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), scale,
                            date(rs.getTimestamp(6)), date(rs.getTimestamp(7)), localDateTime(rs.getTimestamp(8)),
                            money(rs.getBigDecimal(9), scale), money(rs.getBigDecimal(11), scale), rs.getString(10));
                }, userId);
    }

    private List<NoticeRecord> noticesFor(String username) {
        long userId = user(username).id();
        return jdbc.query("SELECT n.fn_notice_id,a.fa_account_id,a.fa_account_number,c.fcr_iso_code,c.fcr_decimal_places,n.fn_notice_type,n.fn_notice_date,n.fn_channel,n.fn_delivery_status " +
                        "FROM fd_notices n JOIN fd_accounts a ON a.fa_account_id=n.fn_account_id JOIN fd_currency_ref c ON c.fcr_currency_id=a.fa_currency_id " +
                        "WHERE a.fa_primary_holder_id=? ORDER BY n.fn_notice_date DESC,n.fn_notice_id DESC",
                (rs, row) -> new NoticeRecord(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getString(6),
                        localDateTime(rs.getTimestamp(7)), rs.getString(8), rs.getString(9)), userId);
    }

    private UserRef user(String username) {
        UserRef user = jdbc.query("SELECT fur_user_id,fur_full_name,fur_username FROM fd_user_ref WHERE fur_username=?",
                rs -> rs.next() ? new UserRef(rs.getLong(1), rs.getString(2), rs.getString(3)) : null, username);
        if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer report not found");
        return user;
    }

    private ResponseEntity<byte[]> portfolioCsv(PortfolioReport report, String filename) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"customerUsername", "fullName", "accountId", "accountNumber", "currencyCode", "principal", "annualRate", "openDate", "maturityDate", "projectedMaturityAmount", "status"});
        for (DepositView row : report.accounts()) rows.add(new String[]{report.customerUsername(), report.fullName(), Long.toString(row.accountId()), row.accountNumber(),
                row.currencyCode(), decimal(row.principal()), decimal(row.annualRate()), row.openDate().toString(), row.maturityDate().toString(), decimal(row.maturityAmount()), row.status()});
        return csv(filename, rows);
    }

    private ResponseEntity<byte[]> interestHistoryCsv(List<InterestHistoryRow> history, String filename) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"accountId", "accountNumber", "accrualDate", "currencyCode", "decimalPlaces", "accruedInterest", "cumulativeAccruedInterest", "capitalizedAmount"});
        for (InterestHistoryRow row : history) rows.add(new String[]{Long.toString(row.accountId()), row.accountNumber(), row.accrualDate().toString(), row.currencyCode(),
                Integer.toString(row.decimalPlaces()), decimal(row.accruedInterest()), decimal(row.cumulativeAccruedInterest()), decimal(row.capitalizedAmount())});
        return csv(filename, rows);
    }

    private ResponseEntity<byte[]> statementCsv(List<StatementRecord> statements, String filename) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"statementId", "accountId", "accountNumber", "currencyCode", "periodStart", "periodEnd", "generatedAt", "contractedPrincipal", "interestAccruedInPeriod", "accountStatus"});
        for (StatementRecord row : statements) rows.add(new String[]{Long.toString(row.statementId()), Long.toString(row.accountId()), row.accountNumber(), row.currencyCode(),
                text(row.periodStart()), text(row.periodEnd()), text(row.generatedAt()), decimal(row.contractedPrincipal()), decimal(row.interestAccruedInPeriod()), row.accountStatus()});
        return csv(filename, rows);
    }

    private ResponseEntity<byte[]> noticeCsv(List<NoticeRecord> notices, String filename) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"noticeId", "accountId", "accountNumber", "currencyCode", "noticeType", "recordedAt", "channel", "deliveryStatus"});
        for (NoticeRecord row : notices) rows.add(new String[]{Long.toString(row.noticeId()), Long.toString(row.accountId()), row.accountNumber(), row.currencyCode(),
                row.noticeType(), text(row.recordedAt()), row.channel(), row.deliveryStatus()});
        return csv(filename, rows);
    }

    private ResponseEntity<byte[]> csv(String filename, List<String[]> rows) {
        StringBuilder content = new StringBuilder();
        for (String[] row : rows) content.append(csvRow(row)).append("\r\n");
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(content.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String csvRow(String[] row) {
        List<String> cells = new ArrayList<>(row.length);
        for (String cell : row) {
            String value = cell == null ? "" : cell;
            if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 && !value.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?")) value = "'" + value;
            cells.add("\"" + value.replace("\"", "\"\"") + "\"");
        }
        return String.join(",", cells);
    }

    private static DepositView deposit(long id, String number, long holder, String username, BigDecimal principal, String currency, int scale,
                                       BigDecimal rate, java.sql.Date opened, java.sql.Date maturity, BigDecimal maturityAmount, String status) {
        return new DepositView(id, number, holder, username, money(principal, scale), currency, rate,
                opened == null ? null : opened.toLocalDate(), maturity == null ? null : maturity.toLocalDate(), money(maturityAmount, scale), status);
    }

    private static LocalDate date(Timestamp value) { return value == null ? null : value.toLocalDateTime().toLocalDate(); }
    private static LocalDateTime localDateTime(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
    private static BigDecimal money(BigDecimal amount, int scale) { return (amount == null ? BigDecimal.ZERO : amount).setScale(Math.max(0, scale), RoundingMode.HALF_UP); }
    private static String decimal(BigDecimal value) { return value == null ? "" : value.toPlainString(); }
    private static String text(Object value) { return value == null ? "" : value.toString(); }

    private record UserRef(long id, String fullName, String username) {}
    public record CurrencyTotals(String currencyCode, int decimalPlaces, long activeCount, BigDecimal totalPrincipal, BigDecimal totalAccruedInterest, BigDecimal averageInterestRate) {}
    public record DepositView(long accountId, String accountNumber, long customerId, String username, BigDecimal principal, String currencyCode, BigDecimal annualRate, LocalDate openDate, LocalDate maturityDate, BigDecimal maturityAmount, String status) {}
    public record PortfolioReport(String customerUsername, String fullName, List<CurrencyTotals> currencies, List<DepositView> accounts) {}
    public record OperationalReport(List<CurrencyTotals> currencies, List<DepositView> maturingSoonAccounts) {}
    public record FdSummaryRow(String productCode, String productName, String currencyCode, int decimalPlaces, String status, long totalAccounts,
                               BigDecimal totalPrincipal, BigDecimal totalAccruedInterest, BigDecimal storedMaturityAmountTotal) {}
    public record MaturitySummaryRow(LocalDate maturityDate, String productCode, String productName, String currencyCode, int decimalPlaces, long accountsDue,
                                     BigDecimal totalPrincipal, BigDecimal totalAccruedInterest, BigDecimal storedMaturityAmountTotal) {}
    public record InterestHistoryRow(long accountId, String accountNumber, LocalDate accrualDate, BigDecimal accruedInterest,
                                     BigDecimal cumulativeAccruedInterest, BigDecimal capitalizedAmount, String currencyCode, int decimalPlaces) {}
    public record StatementRecord(long statementId, long accountId, String accountNumber, String currencyCode, int decimalPlaces,
                                  LocalDate periodStart, LocalDate periodEnd, LocalDateTime generatedAt, BigDecimal contractedPrincipal,
                                  BigDecimal interestAccruedInPeriod, String accountStatus) {}
    public record NoticeRecord(long noticeId, long accountId, String accountNumber, String currencyCode, int decimalPlaces, String noticeType,
                               LocalDateTime recordedAt, String channel, String deliveryStatus) {}
}
