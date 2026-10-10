package com.bank.fd.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FdCompoundCalculatorTest {
    @Test
    void accruesDailyAndCapitalizesOnCalendarQuarterAnniversaries() {
        var result = FdCompoundCalculator.maturityAmount(new BigDecimal("1000.00"), new BigDecimal("0.1200"),
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1), 2);

        assertEquals(new BigDecimal("1125.50"), result.maturityAmount());
        assertEquals(0, result.interestAmount().compareTo(new BigDecimal("125.50")));
        assertTrue(result.maturityAmount().compareTo(new BigDecimal("1120.00")) > 0);
    }

    @Test
    void capitalizesShortFinalTermAtMaturityAndKeepsOpeningDateConvention() {
        assertEquals(new BigDecimal("1000.00"), FdCompoundCalculator.maturityAmount(
                new BigDecimal("1000.00"), new BigDecimal("0.1000"), LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 1), 2).maturityAmount());
        assertTrue(FdCompoundCalculator.isCapitalizationDate(LocalDate.of(2025, 1, 31),
                LocalDate.of(2025, 4, 30), LocalDate.of(2025, 7, 31)));
    }
}
