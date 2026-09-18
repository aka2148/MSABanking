package com.bank.fd.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/** Provides one explicit timestamp and business-date boundary for FD work. */
public final class FdTimePolicy {

    private final Clock clock;
    private final ZoneId businessZone;

    public FdTimePolicy(Clock clock, ZoneId businessZone) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.businessZone = Objects.requireNonNull(businessZone, "businessZone must not be null");
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate businessDate() {
        return LocalDate.ofInstant(now(), businessZone);
    }

    public ZoneId businessZone() {
        return businessZone;
    }
}
