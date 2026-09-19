package com.bank.fd;

import com.bank.fd.entity.FdAccount;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.FdProductReferenceRepository;
import com.bank.fd.repository.FdRuleOutcomeReferenceRepository;
import com.bank.fd.repository.FdUserReferenceRepository;
import com.bank.fd.repository.TransactionTypeRepository;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FdBookingSchemaTest {

    @Test
    void accountMapsAnIndependentPolicyVersionSnapshot() throws NoSuchFieldException {
        var field = FdAccount.class.getDeclaredField("policyVersion");

        assertEquals("fa_policy_version", field.getAnnotation(Column.class).name());
        assertEquals(String.class, field.getType());
    }

    @Test
    void referenceRepositoriesExposeStableLocalLookupContracts() throws NoSuchMethodException {
        assertNotNull(FdProductReferenceRepository.class.getMethod("findByProductCode", String.class));
        assertNotNull(FdCurrencyReferenceRepository.class.getMethod("findByIsoCode", String.class));
        assertNotNull(FdRuleOutcomeReferenceRepository.class.getMethod("findByOutcomeTypeAndOutcomeValue", String.class, String.class));
        assertNotNull(FdUserReferenceRepository.class.getMethod("findByUserUuid", String.class));
        assertNotNull(TransactionTypeRepository.class.getMethod("findIdByCode", String.class));
    }

    @Test
    void migrationContainsBookingSnapshotAndDiagnosticGuards() throws Exception {
        String migration = new String(
                getClass().getResourceAsStream("/db/migration/V7__fd_booking_snapshot.sql").readAllBytes());

        assertNotNull(migration);
        assertEquals(true, migration.contains("fa_policy_version"));
        assertEquals(true, migration.contains("academic-v1"));
        assertEquals(true, migration.contains("fur_user_uuid"));
        assertEquals(true, migration.contains("DEPOSIT"));
        assertEquals(true, migration.contains("RAISE EXCEPTION"));
    }
}
