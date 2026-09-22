package com.bank.fd.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrematurePenaltyTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = START.plusDays(182);

    @Test
    void knownValueMatchesPenalizedRateComputation() {
        BigDecimal principal = new BigDecimal("100000");
        BigDecimal contractedRate = new BigDecimal("0.0650");
        BigDecimal penaltyPoints = PrematurePenalty.parsePenaltyPoints("1.00").orElseThrow();

        BigDecimal expected = FdInterestCalculator.interestForPeriod(
                principal, new BigDecimal("0.0550"), START, END);
        assertEquals(expected, PrematurePenalty.prematureInterest(
                principal, contractedRate, penaltyPoints, START, END));
    }

    @Test
    void zeroFloorWhenPenaltyExceedsRate() {
        BigDecimal principal = new BigDecimal("100000");
        BigDecimal contractedRate = new BigDecimal("0.0650");
        BigDecimal penaltyPoints = PrematurePenalty.parsePenaltyPoints("10.00").orElseThrow();

        assertEquals(new BigDecimal("0.0000"), PrematurePenalty.penalizedRate(contractedRate, penaltyPoints));
        assertEquals(new BigDecimal("0.00"), PrematurePenalty.prematureInterest(
                principal, contractedRate, penaltyPoints, START, END));
    }

    @Test
    void unsupportedSignalsForMissingOrMalformedRules() {
        assertEquals(Optional.empty(), PrematurePenalty.parsePenaltyPoints(null));
        assertEquals(Optional.empty(), PrematurePenalty.parsePenaltyPoints("   "));
        assertEquals(Optional.empty(), PrematurePenalty.parsePenaltyPoints("abc"));
        assertEquals(Optional.empty(), PrematurePenalty.parsePenaltyPoints("-1.00"));
    }

    @Test
    void settlementEqualsPrincipalPlusInterest() {
        BigDecimal principal = new BigDecimal("100000");
        BigDecimal contractedRate = new BigDecimal("0.0650");
        BigDecimal penaltyPoints = PrematurePenalty.parsePenaltyPoints("1.00").orElseThrow();

        BigDecimal interest = PrematurePenalty.prematureInterest(
                principal, contractedRate, penaltyPoints, START, END);
        assertEquals(principal.add(interest).setScale(2), PrematurePenalty.prematureSettlement(
                principal, contractedRate, penaltyPoints, START, END));
        assertTrue(PrematurePenalty.foregoneInterest(
                FdInterestCalculator.interestForPeriod(principal, contractedRate, START, END),
                interest).signum() >= 0);
    }
}
