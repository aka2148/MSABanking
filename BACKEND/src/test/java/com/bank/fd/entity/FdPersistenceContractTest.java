package com.bank.fd.entity;

import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdTransactionRepository;
import jakarta.persistence.Column;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FdPersistenceContractTest {

    @Test
    void newAccountsStartPending() {
        assertEquals(FdStatus.PENDING, new FdAccount().getStatus());
    }

    @Test
    void legacyTermDaysMustMapToWholeThirtyDayMonths() {
        FdAccount account = new FdAccount();

        account.setTermDays(60);

        assertEquals(2, account.getTenureMonths());
        assertThrows(IllegalArgumentException.class, () -> account.setTermDays(365));
    }

    @Test
    void rowVersionUsesNumericLifecycleColumn() throws NoSuchFieldException {
        Field rowVersion = FdAccount.class.getDeclaredField("rowVersion");

        assertNotNull(rowVersion.getAnnotation(Version.class));
        assertEquals("fa_row_version", rowVersion.getAnnotation(Column.class).name());
        assertEquals(Long.class, rowVersion.getType());

        Field legacyVersion = FdAccount.class.getDeclaredField("version");
        assertEquals(String.class, legacyVersion.getType());
        assertNull(legacyVersion.getAnnotation(Version.class));
    }

    @Test
    void lifecycleAndHistoryRepositoriesExposeDeterministicContracts() throws NoSuchMethodException {
        Method findForUpdate = FdAccountRepository.class.getMethod("findByIdForUpdate", Long.class);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, findForUpdate.getAnnotation(Lock.class).value());
        assertNotNull(findForUpdate.getAnnotation(org.springframework.data.jpa.repository.Query.class));
        assertNotNull(FdAccountRepository.class.getMethod("findByPrimaryHolderIdAndStatus", Long.class, FdStatus.class));

        Method history = FdTransactionRepository.class
                .getMethod("findByAccountIdOrderByTransactionDateAscIdAsc", Long.class);
        assertFalse(history.getReturnType().isArray());
    }
}
