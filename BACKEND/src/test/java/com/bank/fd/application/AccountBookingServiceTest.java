package com.bank.fd.application;

import com.bank.fd.api.dto.BookingRequest;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdCurrencyReference;
import com.bank.fd.entity.FdUserReference;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.service.AccountNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountBookingServiceTest {
    private final ProductTermsPort termsPort = mock(ProductTermsPort.class);
    private final FdActorResolver actorResolver = mock(FdActorResolver.class);
    private final FdAccountRepository accountRepository = mock(FdAccountRepository.class);
    private final AccountNumberGenerator accountNumberGenerator = mock(AccountNumberGenerator.class);
    private final FdCurrencyReferenceRepository currencyRepository = mock(FdCurrencyReferenceRepository.class);
    private AccountBookingService service;

    @BeforeEach
    void setUp() throws Exception {
        var time = new FdTimePolicy(Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), ZoneId.of("UTC")), ZoneId.of("UTC"));
        service = new AccountBookingService(termsPort, actorResolver, accountRepository, accountNumberGenerator, time);
        Field field = AccountBookingService.class.getDeclaredField("currencyReferenceRepository");
        field.setAccessible(true);
        field.set(service, currencyRepository);
    }

    @Test
    void booksPendingSnapshotWithSimpleActual365Estimate() {
        when(termsPort.resolveActiveTerms(any(), any())).thenReturn(
                new ProductTerms(7L, "FD7", "Academic FD", "USD", new BigDecimal("100"),
                        new BigDecimal("10000"), 3, 12, new BigDecimal("0.0650"), "SIMPLE", "AT_MATURITY", 9L, "NONE"));
        FdUserReference holder = new FdUserReference(); holder.setId(41L);
        when(actorResolver.resolve("alice")).thenReturn(holder);
        when(accountNumberGenerator.generateNextAccountNumber("000")).thenReturn("FD-BR000-00000001");
        FdCurrencyReference currency = new FdCurrencyReference(); currency.setId(3L); currency.setIsoCode("USD");
        when(currencyRepository.findByIsoCode("USD")).thenReturn(Optional.of(currency));
        when(accountRepository.save(any(FdAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.book("alice", new BookingRequest(7L, new BigDecimal("1000.00"), 3, "usd"));

        assertEquals("PENDING", response.status());
        assertFalse(response.fundingRecorded());
        assertEquals(new BigDecimal("1016.03"), response.maturityAmount());
        verify(accountRepository).save(any(FdAccount.class));
        verify(accountNumberGenerator).generateNextAccountNumber("000");
    }

    @Test
    void rejectsUnsupportedCompoundingBeforeSave() {
        when(termsPort.resolveActiveTerms(any(), any())).thenReturn(
                new ProductTerms(7L, "FD7", "Academic FD", "USD", new BigDecimal("100"),
                        new BigDecimal("10000"), 3, 12, new BigDecimal("0.0650"), "MONTHLY", "AT_MATURITY", 9L, "NONE"));
        assertThrows(IllegalArgumentException.class, () -> service.book("alice",
                new BookingRequest(7L, new BigDecimal("1000.00"), 3, "USD")));
        verify(accountRepository, never()).save(any());
    }
}
