package com.bank.fdservice;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Writes metadata-only lifecycle notices and monthly statement records. */
@Service
public class FdLifecycleRecordService {
    private final JdbcTemplate jdbc;

    public FdLifecycleRecordService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordNoticeIfMissing(long accountId, String type, LocalDate businessDate, String actor) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM fd_notices WHERE fn_account_id=? AND fn_notice_type=?",
                Integer.class, accountId, type);
        if (exists != null && exists > 0) return false;
        jdbc.update("INSERT INTO fd_notices(fn_account_id,fn_notice_type,fn_notice_date,fn_channel,fn_delivery_status,fn_created_by) " +
                        "VALUES(?,?,?,'IN_APP','RECORDED',?)",
                accountId, type, businessDate.atStartOfDay(), actor);
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordMonthlyStatementIfMissing(long accountId, LocalDate periodStart, LocalDate periodEnd) {
        LocalDateTime start = periodStart.atStartOfDay();
        LocalDateTime end = periodEnd.atStartOfDay();
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM fd_statements WHERE fs_account_id=? AND fs_period_start=? AND fs_period_end=?",
                Integer.class, accountId, start, end);
        if (exists != null && exists > 0) return false;
        jdbc.update("INSERT INTO fd_statements(fs_account_id,fs_period_start,fs_period_end,fs_generated_date,fs_created_by) " +
                        "VALUES(?,?,?,UTC_TIMESTAMP(6),'fd-batch')",
                accountId, start, end);
        return true;
    }
}
