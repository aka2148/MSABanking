package com.bank.fd.domain;

import com.bank.common.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FdTimePolicyTest {
    private static final Instant SYSTEM_TIME = Instant.parse("2026-01-01T23:30:00Z");

    @Test
    void advanceMovesEffectiveTimeAndBusinessDateWithoutChangingSystemTime() {
        FdTimePolicy policy = policy();

        policy.advanceDays(90);
        policy.advanceDays(30);

        assertEquals(SYSTEM_TIME, policy.systemNow());
        assertEquals(SYSTEM_TIME.plusSeconds(120L * 24 * 60 * 60), policy.now());
        assertEquals(LocalDate.of(2026, 5, 1), policy.businessDate());
        assertTrue(policy.isSimulated());
    }

    @Test
    void resetRestoresSystemTimeAndClearsSimulationFlag() {
        FdTimePolicy policy = policy();
        policy.advanceDays(90);

        policy.reset();

        assertEquals(SYSTEM_TIME, policy.now());
        assertEquals(LocalDate.of(2026, 1, 1), policy.businessDate());
        assertFalse(policy.isSimulated());
    }

    @Test
    void rejectsInvalidOrOutOfRangeAdvancesWithoutChangingTheClock() {
        FdTimePolicy policy = policy();

        assertThrows(InvalidInputException.class, () -> policy.advanceDays(0));
        assertThrows(InvalidInputException.class, () -> policy.advanceDays(-1));
        assertThrows(InvalidInputException.class, () -> policy.advanceDays(FdTimePolicy.MAX_ADVANCE_DAYS + 1));
        policy.advanceDays(FdTimePolicy.MAX_ADVANCE_DAYS);
        assertThrows(InvalidInputException.class, () -> policy.advanceDays(1));
        assertEquals(SYSTEM_TIME.plusSeconds((long) FdTimePolicy.MAX_ADVANCE_DAYS * 24 * 60 * 60), policy.now());
    }

    private static FdTimePolicy policy() {
        return new FdTimePolicy(Clock.fixed(SYSTEM_TIME, ZoneOffset.UTC), ZoneId.of("UTC"));
    }
}
