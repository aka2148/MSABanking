package com.bank.fd.service;

import com.bank.fd.repository.FdAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class AccountNumberGeneratorTest {

    @Mock
    private FdAccountRepository fdAccountRepository;

    @InjectMocks
    private AccountNumberGenerator accountNumberGenerator;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testGenerateFirstAccountNumber() {
        // Mock repository returning null (no accounts exist for branch 001)
        when(fdAccountRepository.findMaxAccountNumberByPrefix("FD-BR001-")).thenReturn(null);

        String accountNumber = accountNumberGenerator.generateNextAccountNumber("001");

        assertEquals("FD-BR001-00000001", accountNumber);
    }

    @Test
    public void testGenerateIncrementedAccountNumber() {
        // Mock repository returning current max account number
        when(fdAccountRepository.findMaxAccountNumberByPrefix("FD-BR001-")).thenReturn("FD-BR001-00000123");

        String accountNumber = accountNumberGenerator.generateNextAccountNumber("001");

        assertEquals("FD-BR001-00000124", accountNumber);
    }

    @Test
    public void testGenerateWithNullOrEmptyBranchCode() {
        // Mock repository returning max account number for default branch "000"
        when(fdAccountRepository.findMaxAccountNumberByPrefix("FD-BR000-")).thenReturn("FD-BR000-00000005");

        String accountNumberNull = accountNumberGenerator.generateNextAccountNumber(null);
        String accountNumberEmpty = accountNumberGenerator.generateNextAccountNumber("");

        assertEquals("FD-BR000-00000006", accountNumberNull);
        assertEquals("FD-BR000-00000006", accountNumberEmpty);
    }
}
