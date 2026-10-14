package com.bank.fd.application;
import com.bank.common.exception.*;
import com.bank.fd.api.dto.CreateSavingsRequest;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.entity.*;
import com.bank.fd.repository.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SavingsAccountServiceTest {
    SavingsAccountRepository accounts = mock(SavingsAccountRepository.class);
    FdCurrencyReferenceRepository currencies = mock(FdCurrencyReferenceRepository.class);
    FdActorResolver actors = mock(FdActorResolver.class);
    FdIdempotencyGuard guard = mock(FdIdempotencyGuard.class);
    SavingsAccountService service;
    @BeforeEach void setup() {
        var holder = new FdUserReference(); holder.setId(41L);
        when(actors.resolve("alice")).thenReturn(holder);
        var currency = new FdCurrencyReference(); currency.setId(3L); currency.setIsoCode("INR"); currency.setDecimalPlaces(2);
        when(currencies.findByIsoCode("INR")).thenReturn(Optional.of(currency));
        when(currencies.findById(3L)).thenReturn(Optional.of(currency));
        var time = new FdTimePolicy(Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC), ZoneOffset.UTC);
        service = new SavingsAccountService(accounts, currencies, actors, time, guard);
    }
    private SavingsAccount saved() {
        var a = new SavingsAccount(); a.setId(8L); a.setHolderId(41L); a.setCurrencyId(3L);
        a.setAccountNumber("SA-TEST"); a.setDisplayName("My savings"); a.setBalance(new BigDecimal("0.00"));
        return a;
    }
    @Test void createsZeroBalanceForAuthenticatedOwnerUsingSimulatedTime() {
        when(accounts.save(any())).thenAnswer(i -> { SavingsAccount a=i.getArgument(0); a.setId(8L); return a; });
        var result = service.create("alice", new CreateSavingsRequest(" My savings ", "inr"), "key");
        assertEquals(new BigDecimal("0.00"), result.balance());
        assertEquals("INR", result.currencyCode()); assertEquals("My savings", result.displayName());
        assertEquals(OffsetDateTime.parse("2026-01-15T00:00:00Z"), result.createdDate());
        verify(accounts).save(argThat(a -> a.getHolderId().equals(41L) && a.getStatus().equals("ACTIVE")));
    }
    @Test void replaysCreationWithoutSavingAnotherAccount() {
        var claim=new FdOperationIdempotency(); claim.setAggregateId(8L);
        when(guard.claim(anyString(), anyString(), anyString(), anyString())).thenReturn(claim);
        when(accounts.findById(8L)).thenReturn(Optional.of(saved()));
        assertEquals(8L, service.create("alice", new CreateSavingsRequest("My savings", "INR"), "key").accountId());
        verify(accounts, never()).save(any());
    }
    @Test void hidesOtherCustomersSavings() {
        var a=saved(); a.setHolderId(42L); when(accounts.findById(8L)).thenReturn(Optional.of(a));
        assertThrows(ResourceNotFoundException.class, () -> service.get("alice", 8L));
        assertThrows(ResourceNotFoundException.class, () -> service.requireFundingAccount(8L, 41L, 3L));
    }
    @Test void rejectsCurrencyMismatchAndInactiveFundingSource() {
        var a=saved(); when(accounts.findById(8L)).thenReturn(Optional.of(a));
        assertThrows(InvalidInputException.class, () -> service.requireFundingAccount(8L, 41L, 4L));
        a.setStatus("INACTIVE");
        assertThrows(InvalidInputException.class, () -> service.requireFundingAccount(8L, 41L, 3L));
    }
    @Test void listsOnlyAuthenticatedHoldersAccounts() {
        when(accounts.findByHolderIdOrderByIdAsc(41L)).thenReturn(List.of(saved()));
        assertEquals(1, service.list("alice").size()); verify(accounts).findByHolderIdOrderByIdAsc(41L);
    }
    @Test void rejectsUnsupportedCurrencyAndBlankNameBeforeSave() {
        assertThrows(InvalidInputException.class, () -> service.create("alice", new CreateSavingsRequest(" ", "INR"), null));
        when(currencies.findByIsoCode("XXX")).thenReturn(Optional.empty());
        assertThrows(InvalidInputException.class, () -> service.create("alice", new CreateSavingsRequest("Name", "XXX"), null));
        verify(accounts, never()).save(any());
    }
}
