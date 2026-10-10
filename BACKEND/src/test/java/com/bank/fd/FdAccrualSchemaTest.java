package com.bank.fd;

import com.bank.fd.entity.FdInterestAccrual;
import com.bank.fd.repository.FdInterestAccrualRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FdAccrualSchemaTest {

    @Test
    void accrualEntityMapsTheInterestAccrualTable() throws NoSuchFieldException {
        Table table = FdInterestAccrual.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("fd_interest_accrual", table.name());
        assertEquals("public", table.schema());

        assertEquals("fia_accrual_id", field(FdInterestAccrual.class, "id").getAnnotation(Column.class).name());
        assertEquals("fia_account_id", field(FdInterestAccrual.class, "accountId").getAnnotation(Column.class).name());
        assertEquals("fia_accrual_date", field(FdInterestAccrual.class, "accrualDate").getAnnotation(Column.class).name());
        assertEquals("fia_accrued_amount", field(FdInterestAccrual.class, "accruedAmount").getAnnotation(Column.class).name());
        assertEquals("fia_cumulative_accrued", field(FdInterestAccrual.class, "cumulativeAccrued").getAnnotation(Column.class).name());
        assertEquals("fia_compounding_balance", field(FdInterestAccrual.class, "compoundingBalance").getAnnotation(Column.class).name());
        assertEquals("fia_capitalized_amount", field(FdInterestAccrual.class, "capitalizedAmount").getAnnotation(Column.class).name());
        assertEquals("fia_currency_id", field(FdInterestAccrual.class, "currencyId").getAnnotation(Column.class).name());
        assertEquals("fia_uuid", field(FdInterestAccrual.class, "uuid").getAnnotation(Column.class).name());
    }

    @Test
    void repositoryExposesAccrualLookupContracts() throws NoSuchMethodException {
        assertNotNull(FdInterestAccrualRepository.class.getMethod(
                "findByAccountIdOrderByAccrualDateAscIdAsc", Long.class));
        assertNotNull(FdInterestAccrualRepository.class.getMethod(
                "findByAccountIdAndAccrualDate", Long.class, OffsetDateTime.class));
    }

    @Test
    void migrationProvisionsAccrualAndMaturityCodesWithGuards() throws Exception {
        String migration = new String(
                getClass().getResourceAsStream("/db/migration/V8__fd_accrual_maturity.sql").readAllBytes());

        assertNotNull(migration);
        assertTrue(migration.contains("INTEREST_ACCRUAL"));
        assertTrue(migration.contains("MATURITY"));
        assertTrue(migration.contains("ON CONFLICT (tt_txn_type_code) DO NOTHING"));
        assertTrue(migration.contains("RAISE EXCEPTION"));
        assertTrue(migration.contains("uq_fd_interest_accrual_account_date"));
        assertTrue(migration.contains("uq_fd_interest_accrual_fia_uuid"));
    }

    @Test
    void v20MigrationAddsCurrencyRateIdentityAndCompoundAccrualStorage() throws Exception {
        String migration = new String(getClass().getResourceAsStream(
                "/db/migration/V20__tiered_fd_rates_and_compounding.sql").readAllBytes());
        assertTrue(migration.contains("FD_TENURE_RATE"));
        assertTrue(migration.contains("fror_source_outcome_id"));
        assertTrue(migration.contains("fcr_iso_code"));
        assertTrue(migration.contains("fia_compounding_balance"));
        assertTrue(migration.contains("fia_capitalized_amount"));
        assertTrue(migration.contains("'USD'"));
        assertTrue(migration.contains("'INR'"));
    }

    private static java.lang.reflect.Field field(Class<?> type, String name) throws NoSuchFieldException {
        java.lang.reflect.Field field = type.getDeclaredField(name);
        assertNotNull(field.getAnnotation(Column.class), "Missing @Column on " + name);
        return field;
    }
}
