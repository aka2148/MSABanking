package com.bank.fdservice;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostingServiceTest {
    @Test void acceptsBalancedCurrencyScaledLines() {
        PostingService.validateLines(List.of(
                new PostingService.LedgerLine("CASH_TELLER", null, new BigDecimal("10.00"), new BigDecimal("0.00")),
                new PostingService.LedgerLine("SAVINGS", 1L, new BigDecimal("0.00"), new BigDecimal("10.00"))), 2);
    }
    @Test void rejectsUnbalancedLines() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> PostingService.validateLines(List.of(
                new PostingService.LedgerLine("CASH_TELLER", null, new BigDecimal("10.00"), BigDecimal.ZERO),
                new PostingService.LedgerLine("SAVINGS", 1L, BigDecimal.ZERO, new BigDecimal("9.00"))), 2));
        assertEquals(400, error.getStatusCode().value());
    }
    @Test void rejectsAmountsBeyondCurrencyScale() {
        assertThrows(ResponseStatusException.class, () -> PostingService.validateLines(List.of(
                new PostingService.LedgerLine("CASH_TELLER", null, new BigDecimal("10.01"), BigDecimal.ZERO),
                new PostingService.LedgerLine("SAVINGS", 1L, BigDecimal.ZERO, new BigDecimal("10.01"))), 0));
    }
    @Test void rejectsLineWithBothSidesPositive() {
        assertThrows(ResponseStatusException.class, () -> PostingService.validateLines(List.of(
                new PostingService.LedgerLine("CASH_TELLER", null, new BigDecimal("10.00"), BigDecimal.ZERO),
                new PostingService.LedgerLine("SAVINGS", 1L, new BigDecimal("1.00"), new BigDecimal("10.00"))), 2));
    }
}
