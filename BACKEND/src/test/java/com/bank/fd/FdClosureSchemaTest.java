package com.bank.fd;

import com.bank.fd.entity.FdPrematureClosure;
import com.bank.fd.repository.FdPrematureClosureRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FdClosureSchemaTest {

    @Test
    void closureEntityMapsThePrematureClosureTable() throws NoSuchFieldException {
        Table table = FdPrematureClosure.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("fd_premature_closure", table.name());
        assertEquals("public", table.schema());

        assertEquals("fpc_closure_id", field(FdPrematureClosure.class, "id").getAnnotation(Column.class).name());
        assertEquals("fpc_account_id", field(FdPrematureClosure.class, "accountId").getAnnotation(Column.class).name());
        assertEquals("fpc_closure_date", field(FdPrematureClosure.class, "closureDate").getAnnotation(Column.class).name());
        assertEquals("fpc_penalty_pct_applied", field(FdPrematureClosure.class, "penaltyPctApplied").getAnnotation(Column.class).name());
        assertEquals("fpc_penalty_amount", field(FdPrematureClosure.class, "penaltyAmount").getAnnotation(Column.class).name());
        assertEquals("fpc_net_payable_amount", field(FdPrematureClosure.class, "netPayableAmount").getAnnotation(Column.class).name());
        assertEquals("fpc_currency_id", field(FdPrematureClosure.class, "currencyId").getAnnotation(Column.class).name());
        assertEquals("fpc_reason", field(FdPrematureClosure.class, "reason").getAnnotation(Column.class).name());
        assertEquals("fpc_applied_rule_outcome_id", field(FdPrematureClosure.class, "appliedRuleOutcomeId").getAnnotation(Column.class).name());
        assertEquals("fpc_approved_by", field(FdPrematureClosure.class, "approvedBy").getAnnotation(Column.class).name());
        assertEquals("fpc_status", field(FdPrematureClosure.class, "status").getAnnotation(Column.class).name());
        assertEquals("fpc_uuid", field(FdPrematureClosure.class, "uuid").getAnnotation(Column.class).name());
    }

    @Test
    void repositoryExposesClosureLookupContracts() throws NoSuchMethodException {
        assertNotNull(FdPrematureClosureRepository.class.getMethod(
                "findByAccountIdOrderByClosureDateAscIdAsc", Long.class));
        assertNotNull(FdPrematureClosureRepository.class.getMethod(
                "findByAccountIdAndStatus", Long.class, String.class));
    }

    @Test
    void migrationProvisionsClosureAndPenaltyCodesWithGuards() throws Exception {
        String migration = new String(
                getClass().getResourceAsStream("/db/migration/V9__fd_closure_history.sql").readAllBytes());

        assertNotNull(migration);
        assertTrue(migration.contains("CLOSURE"));
        assertTrue(migration.contains("PENALTY"));
        assertTrue(migration.contains("ON CONFLICT (tt_txn_type_code) DO NOTHING"));
        assertTrue(migration.contains("RAISE EXCEPTION"));
        assertTrue(migration.contains("fpc_status"));
        assertTrue(migration.contains("PENDING"));
        assertTrue(migration.contains("APPROVED"));
    }

    private static java.lang.reflect.Field field(Class<?> type, String name) throws NoSuchFieldException {
        java.lang.reflect.Field field = type.getDeclaredField(name);
        assertNotNull(field.getAnnotation(Column.class), "Missing @Column on " + name);
        return field;
    }
}
