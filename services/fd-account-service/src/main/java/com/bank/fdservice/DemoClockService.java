package com.bank.fdservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class DemoClockService {
    private static final int MAX_ADVANCE_DAYS = 3650;
    private final JdbcTemplate jdbc;
    private final ZoneId zone;

    public DemoClockService(JdbcTemplate jdbc,
                            @Value("${app.demo-clock.business-zone:UTC}") String zone) {
        this.jdbc = jdbc;
        this.zone = ZoneId.of(zone);
    }

    public ClockState status() {
        int offset = currentOffset();
        Instant system = Instant.now();
        Instant effective = system.plusSeconds(offset * 86_400L);
        return new ClockState(system, effective, LocalDate.ofInstant(effective, zone), zone.getId(), offset > 0);
    }

    public LocalDate businessDate() { return status().businessDate(); }

    @Transactional
    public ClockState advance(int days) {
        if (days < 1 || days > MAX_ADVANCE_DAYS)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clock advance must be between 1 and 3650 days");
        Integer offset = jdbc.queryForObject("SELECT fdc_offset_days FROM fd_demo_clock WHERE fdc_clock_id=1 FOR UPDATE", Integer.class);
        if (offset == null || offset + days > MAX_ADVANCE_DAYS)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Effective clock cannot be advanced more than 3650 days from system time");
        jdbc.update("UPDATE fd_demo_clock SET fdc_offset_days=?,fdc_updated_at=UTC_TIMESTAMP(6) WHERE fdc_clock_id=1", offset + days);
        return status();
    }

    @Transactional
    public ClockState reset() {
        jdbc.update("UPDATE fd_demo_clock SET fdc_offset_days=0,fdc_updated_at=UTC_TIMESTAMP(6) WHERE fdc_clock_id=1");
        return status();
    }

    private int currentOffset() {
        Integer offset = jdbc.queryForObject("SELECT fdc_offset_days FROM fd_demo_clock WHERE fdc_clock_id=1", Integer.class);
        return offset == null ? 0 : offset;
    }

    public record ClockState(Instant systemTime, Instant effectiveTime, LocalDate businessDate,
                             String businessZone, boolean simulationActive) {}
}
