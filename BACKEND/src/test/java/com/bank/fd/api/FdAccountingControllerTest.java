package com.bank.fd.api;
import com.bank.fd.api.controller.FdAccountingController;
import com.bank.fd.application.FdAccessPolicy;
import com.bank.fd.entity.*;
import com.bank.fd.repository.*;
import com.bank.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FdAccountingControllerTest {
    @Test void authorizedFdReadDisplaysItsLinkedSavingsWithoutBroadSavingsAccess() {
        var accounts=mock(FdAccountRepository.class);var journals=mock(FdJournalRepository.class);
        var currencies=mock(FdCurrencyReferenceRepository.class);var access=mock(FdAccessPolicy.class);
        var savings=mock(SavingsAccountRepository.class);
        var controller=new FdAccountingController(accounts,journals,currencies,access,savings);
        var auth=new UsernamePasswordAuthenticationToken("alice","unused",List.of());
        var fd=new FdAccount();fd.setId(1L);fd.setFundingSavingsAccountId(8L);
        var source=new SavingsAccount();source.setId(8L);source.setCurrencyId(3L);source.setBalance(new BigDecimal("5000.00"));
        var currency=new FdCurrencyReference();currency.setIsoCode("INR");
        when(accounts.findById(1L)).thenReturn(Optional.of(fd));when(savings.findById(8L)).thenReturn(Optional.of(source));
        when(currencies.findById(3L)).thenReturn(Optional.of(currency));
        assertEquals(new BigDecimal("5000.00"),controller.fundingSavings(1L,auth).balance());
        verify(access).requireOwned("alice",fd,auth);
        doThrow(new ResourceNotFoundException("FD account not found")).when(access).requireOwned("alice",fd,auth);
        clearInvocations(savings,journals);
        assertThrows(ResourceNotFoundException.class,()->controller.fundingSavings(1L,auth));
        assertThrows(ResourceNotFoundException.class,()->controller.list(1L,auth));
        verifyNoInteractions(savings,journals);
    }
}
