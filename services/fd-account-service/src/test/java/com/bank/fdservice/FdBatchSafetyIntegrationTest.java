package com.bank.fdservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class FdBatchSafetyIntegrationTest {
    private static final String JWT_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("moneypal_fd")
            .withUsername("batch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20))
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("app.jwt.secret", () -> JWT_SECRET);
        properties.add("app.kafka.enabled", () -> false);
        properties.add("app.demo-clock.business-zone", () -> "UTC");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired FdBatchService batches;
    @Autowired DemoClockService clock;
    @Autowired FdLifecycleController lifecycle;

    private long savingsId;

    @BeforeEach
    void seedSyntheticReferences() {
        jdbc.update("UPDATE fd_demo_clock SET fdc_offset_days=0 WHERE fdc_clock_id=1");
        jdbc.update("INSERT IGNORE INTO fd_user_ref(fur_user_id,fur_user_uuid,fur_full_name,fur_user_status,fur_kyc_status) " +
                        "VALUES(980,?,'Batch Probe Customer','ACTIVE','VERIFIED')", "batch-probe-user-980");
        jdbc.update("INSERT IGNORE INTO fd_currency_ref(fcr_currency_id,fcr_iso_code,fcr_currency_name,fcr_decimal_places) " +
                "VALUES(980,'ZB1','Batch Probe Currency',2)");
        jdbc.update("INSERT IGNORE INTO fd_product_ref(fpr_product_id,fpr_product_code,fpr_product_name,fpr_status) " +
                "VALUES(980,'FD-BATCH-PROBE','Batch Probe Product','ACTIVE')");
        jdbc.update("INSERT IGNORE INTO fd_product_currency_ref(product_id,currency_id,minimum_amount,maximum_amount,is_base_currency,source_version,premature_penalty_points) " +
                "VALUES(980,980,1,100000,1,1,1.0000)");
        jdbc.update("INSERT IGNORE INTO fd_product_rate_ref(product_id,currency_id,minimum_tenure_months,maximum_tenure_months,annual_rate,compounding_basis,payout_frequency,source_version) " +
                "VALUES(980,980,1,120,0.0600,'QUARTERLY','AT_MATURITY',1)");
        String accountNumber = "BP" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        jdbc.update("INSERT INTO savings_accounts(sa_account_number,sa_holder_id,sa_currency_id,sa_display_name,sa_balance,sa_status,sa_created_date) " +
                        "VALUES(?,980,980,'Batch Probe Savings',1000,'ACTIVE',UTC_TIMESTAMP(6))", accountNumber);
        savingsId = jdbc.queryForObject("SELECT sa_account_id FROM savings_accounts WHERE sa_account_number=?", Long.class, accountNumber);
    }

    @Test
    void dailyRunIsAtomicPerAccountResumableAndTimezoneStable() {
        LocalDate today = clock.businessDate();
        assertEquals("UTC", clock.status().businessZone());
        long first = insertDueFd("BATCH-ATOMIC-1", today, 0L);
        long failed = insertDueFd("BATCH-ATOMIC-2", today, Long.MAX_VALUE);
        long third = insertDueFd("BATCH-ATOMIC-3", today, 0L);

        FdBatchService.DailyBatchResult partial = batches.runDaily(today);
        assertEquals(3, partial.accountsExamined());
        assertEquals(1, partial.accountsFailed());
        assertEquals(java.util.List.of(failed), partial.failedAccountIds());
        assertEquals(2, partial.maturitiesProcessed());
        assertEquals("MATURED", status(first));
        assertEquals("ACTIVE", status(failed));
        assertEquals("MATURED", status(third));
        assertTrue(accrualCount(first) > 0);
        assertEquals(0, accrualCount(failed), "the failing account's accrual writes must roll back with its maturity update");
        assertTrue(accrualCount(third) > 0, "a failed account must not stop later accounts");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_key=?", Integer.class, "SETTLEMENT:" + first));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_key=?", Integer.class, "INTEREST:" + first + ":MATURITY"));
        assertEquals(0, new BigDecimal("1000.00").compareTo(balance()));

        jdbc.update("UPDATE fd_accounts SET fa_row_version=0 WHERE fa_account_id=?", failed);
        FdBatchService.DailyBatchResult retry = batches.runDaily(today);
        assertEquals(0, retry.accountsFailed());
        assertEquals(1, retry.maturitiesProcessed());
        assertEquals("MATURED", status(failed));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_key=?", Integer.class, "INTEREST:" + failed + ":MATURITY"));

        assertThrows(ResponseStatusException.class, () -> batches.runDaily(clock.businessDate().plusDays(1)));
        assertLocalDateTimeRoundTripsAcrossJvmZones(first);
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM fd_notices WHERE fn_account_id IN (?,?,?) AND fn_notice_type='FD_MATURED'", Integer.class, first, failed, third));

        LocalDate nextMonth = today.plusMonths(1).withDayOfMonth(1);
        int offset = Math.toIntExact(ChronoUnit.DAYS.between(today, nextMonth));
        jdbc.update("UPDATE fd_demo_clock SET fdc_offset_days=? WHERE fdc_clock_id=1", offset);
        long statementAccount = insertActiveFd("BATCH-STATEMENT", today.minusDays(5), nextMonth.plusDays(40));
        jdbc.update("UPDATE fd_accounts SET fa_interest_rate=0.0200,fa_compounding_frequency='YEARLY'," +
                        "fa_terms_snapshot=CAST(? AS JSON) WHERE fa_account_id=?",
                "{\"annualRate\":0.1000,\"compoundingBasis\":\"MONTHLY\",\"currencyScale\":2}", statementAccount);
        FdBatchService.DailyBatchResult monthly = batches.runDaily(nextMonth);
        assertEquals(0, monthly.accountsFailed());
        assertStatement(statementAccount, nextMonth.minusMonths(1).withDayOfMonth(1), nextMonth);
        assertEquals(0, new BigDecimal("0.02739726").compareTo(jdbc.queryForObject(
                "SELECT fia_accrued_amount FROM fd_interest_accrual WHERE fia_account_id=? ORDER BY fia_accrual_date LIMIT 1",
                BigDecimal.class, statementAccount)), "daily accrual must use the frozen annual-rate snapshot");
        batches.runDaily(nextMonth);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fd_statements WHERE fs_account_id=?", Integer.class, statementAccount));

        LocalDate firstJumpBoundary = nextMonth.plusMonths(1).withDayOfMonth(1);
        LocalDate secondJumpBoundary = firstJumpBoundary.plusMonths(1);
        LocalDate target = secondJumpBoundary.plusDays(10);
        offset = Math.toIntExact(ChronoUnit.DAYS.between(today, target));
        jdbc.update("UPDATE fd_demo_clock SET fdc_offset_days=? WHERE fdc_clock_id=1", offset);
        long account = insertActiveFd("BATCH-JUMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                today.minusDays(20), target.plusDays(40));
        FdBatchService.DailyBatchResult firstRun = batches.runDaily(target);
        assertEquals(0, firstRun.accountsFailed());
        assertEquals(java.util.List.of(nextMonth, firstJumpBoundary, secondJumpBoundary), jdbc.query(
                "SELECT fs_period_end FROM fd_statements WHERE fs_account_id=? ORDER BY fs_period_end",
                (rs, row) -> rs.getTimestamp(1).toLocalDateTime().toLocalDate(), account));

        FdBatchService.DailyBatchResult statementRetry = batches.runDaily(target);
        assertEquals(0, statementRetry.accountsFailed());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM fd_statements WHERE fs_account_id=?", Integer.class, account));

        LocalDate advanceTarget = today.plusDays(30);
        jdbc.update("UPDATE fd_demo_clock SET fdc_offset_days=30 WHERE fdc_clock_id=1");

        long maturesDuringAdvance = insertActiveFd("BMJ-" + UUID.randomUUID().toString().substring(0, 12),
                today.minusDays(10), today.plusDays(15));
        long remainsActive = insertActiveFd("BCJ-" + UUID.randomUUID().toString().substring(0, 12),
                today.minusMonths(3).plusDays(15), advanceTarget.plusDays(30));

        FdBatchService.DailyBatchResult result = batches.runDaily(advanceTarget);

        int expectedMaturityRows = Math.toIntExact(ChronoUnit.DAYS.between(today.minusDays(10), today.plusDays(15)));
        int expectedCompoundingRows = Math.toIntExact(ChronoUnit.DAYS.between(today.minusMonths(3).plusDays(15), advanceTarget));
        assertTrue(result.accrualsProcessed() >= 2);
        assertEquals(expectedMaturityRows + expectedCompoundingRows, result.accrualDaysRecorded());
        assertEquals(1, result.maturitiesProcessed());
        assertEquals(expectedMaturityRows, accrualCount(maturesDuringAdvance));
        assertEquals("MATURED", status(maturesDuringAdvance));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_key=?", Integer.class,
                "INTEREST:" + maturesDuringAdvance + ":MATURITY"));
        java.sql.Timestamp maturityTimestamp = jdbc.queryForObject("SELECT ft_txn_date FROM fd_transactions WHERE ft_account_id=? AND ft_txn_type_id=(SELECT tt_txn_type_id FROM transaction_types WHERE tt_txn_type_code='FD_MATURITY')",
                java.sql.Timestamp.class, maturesDuringAdvance);
        assertNotNull(maturityTimestamp);
        assertEquals(today.plusDays(15), maturityTimestamp.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                "maturity activity must use the contractual maturity date, not the later batch date");
        var actor = new UsernamePasswordAuthenticationToken("batch-probe-user-980", "", java.util.List.of());
        actor.setDetails(java.util.Map.of("userId", 980L));
        var maturityActivity = lifecycle.transactions(actor, maturesDuringAdvance).stream()
                .filter(row -> "FD_MATURITY".equals(row.get("event_type"))).findFirst().orElseThrow();
        assertTrue(maturityActivity.get("occurred_at").toString().endsWith("Z"),
                "activity timestamps must be serialized as UTC instants for correct browser conversion");
        assertEquals("Account matured; settlement can be completed using the account's close action", maturityActivity.get("remarks"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_key=?", Integer.class,
                "SETTLEMENT:" + maturesDuringAdvance));
        assertEquals("ACTIVE", status(remainsActive));
        assertEquals(expectedCompoundingRows, accrualCount(remainsActive));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM fd_interest_accrual WHERE fia_account_id=? AND fia_capitalized_amount>0",
                Integer.class, remainsActive) > 0, "the date jump must retain quarterly capitalization dates");
    }

    private void assertLocalDateTimeRoundTripsAcrossJvmZones(long accountId) {
        LocalDateTime expected = LocalDateTime.of(2035, 1, 2, 0, 0);
        jdbc.update("INSERT INTO fd_statements(fs_account_id,fs_period_start,fs_period_end,fs_created_by) VALUES(?,?,?,'timezone-probe')",
                accountId, expected, expected.plusDays(1));
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
            assertEquals(expected, statementStart(accountId, "timezone-probe"));
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertEquals(expected, statementStart(accountId, "timezone-probe"));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private LocalDateTime statementStart(long accountId, String createdBy) {
        return jdbc.query("SELECT fs_period_start FROM fd_statements WHERE fs_account_id=? AND fs_created_by=?",
                rs -> rs.next() ? rs.getObject(1, LocalDateTime.class) : null, accountId, createdBy);
    }

    private void assertStatement(long accountId, LocalDate start, LocalDate end) {
        LocalDateTime actualStart = jdbc.query("SELECT fs_period_start FROM fd_statements WHERE fs_account_id=?",
                rs -> rs.next() ? rs.getObject(1, LocalDateTime.class) : null, accountId);
        LocalDateTime actualEnd = jdbc.query("SELECT fs_period_end FROM fd_statements WHERE fs_account_id=?",
                rs -> rs.next() ? rs.getObject(1, LocalDateTime.class) : null, accountId);
        assertEquals(start.atStartOfDay(), actualStart);
        assertEquals(end.atStartOfDay(), actualEnd);
    }

    private long insertDueFd(String accountNumber, LocalDate today, long rowVersion) {
        return insertFd(accountNumber, today.minusDays(3), today.minusDays(1), rowVersion);
    }

    private long insertActiveFd(String accountNumber, LocalDate open, LocalDate maturity) {
        return insertFd(accountNumber, open, maturity, 0L);
    }

    private long insertFd(String accountNumber, LocalDate open, LocalDate maturity, long rowVersion) {
        jdbc.update("INSERT INTO fd_accounts(fa_account_number,fa_primary_holder_id,fa_product_id,fa_principal_amount,fa_currency_id," +
                        "fa_tenure_months,fa_interest_rate,fa_compounding_frequency,fa_payout_frequency,fa_open_date,fa_maturity_date," +
                        "fa_maturity_amount,fa_status,fa_funding_savings_account_id,fa_row_version) " +
                        "VALUES(?,980,980,100,980,1,0.0600,'QUARTERLY','AT_MATURITY',?,?,100,'ACTIVE',?,?)",
                accountNumber, open.atStartOfDay(), maturity, savingsId, rowVersion);
        return jdbc.queryForObject("SELECT fa_account_id FROM fd_accounts WHERE fa_account_number=?", Long.class, accountNumber);
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT fa_status FROM fd_accounts WHERE fa_account_id=?", String.class, id);
    }

    private int accrualCount(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM fd_interest_accrual WHERE fia_account_id=?", Integer.class, id);
    }

    private BigDecimal balance() {
        return jdbc.queryForObject("SELECT sa_balance FROM savings_accounts WHERE sa_account_id=?", BigDecimal.class, savingsId);
    }
}
