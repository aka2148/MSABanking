package com.bank.fd.domain;

import com.bank.common.exception.InvalidInputException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Provides one explicit timestamp and business-date boundary for FD work. */
@Component
public final class FdTimePolicy {
    public static final int MAX_ADVANCE_DAYS = 3650;
    private static final Duration MAX_TIME_OFFSET = Duration.ofDays(MAX_ADVANCE_DAYS);

    private final Clock clock;
    private final ZoneId businessZone;
    private volatile Duration timeOffset = Duration.ZERO;

    public FdTimePolicy() {
        this(Clock.systemUTC(), ZoneId.of("UTC"));
    }

    public FdTimePolicy(Clock clock, ZoneId businessZone) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.businessZone = Objects.requireNonNull(businessZone, "businessZone must not be null");
    }

    public Instant now() {
        return clock.instant().plus(timeOffset);
    }

    /** Returns the underlying system time before applying the in-memory demo offset. */
    public Instant systemNow() {
        return clock.instant();
    }

    /** Advances the effective application clock by a positive whole number of days. */
    public synchronized void advanceDays(int days) {
        if (days <= 0 || days > MAX_ADVANCE_DAYS) {
            throw new InvalidInputException("Clock advance must be between 1 and " + MAX_ADVANCE_DAYS + " days");
        }
        Duration nextOffset = timeOffset.plusDays(days);
        if (nextOffset.compareTo(MAX_TIME_OFFSET) > 0) {
            throw new InvalidInputException("Effective clock cannot be advanced more than "
                    + MAX_ADVANCE_DAYS + " days from system time");
        }
        clock.instant().plus(nextOffset);
        timeOffset = nextOffset;
    }

    /** Clears the temporary offset. Persisted FD data is not changed. */
    public synchronized void reset() {
        timeOffset = Duration.ZERO;
    }

    public boolean isSimulated() {
        return !timeOffset.isZero();
    }

    public LocalDate businessDate() {
        return LocalDate.ofInstant(now(), businessZone);
    }

    public ZoneId businessZone() {
        return businessZone;
    }
}
