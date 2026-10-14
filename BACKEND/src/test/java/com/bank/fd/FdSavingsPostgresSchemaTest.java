package com.bank.fd;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class FdSavingsPostgresSchemaTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @Test void fullMigrationChainCreatesSavingsAndJournalsWithoutSeedingMoney() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())
            .locations("classpath:db/migration").load().migrate();
        try (Connection db=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
             Statement sql=db.createStatement()) {
            try(var rows=sql.executeQuery("SELECT COUNT(*) FROM public.savings_accounts")) { assertTrue(rows.next());assertEquals(0,rows.getLong(1)); }
            try(var rows=sql.executeQuery("SELECT COUNT(*) FROM public.fd_journals")) { assertTrue(rows.next());assertEquals(0,rows.getLong(1)); }
            try(var rows=sql.executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_name='fd_accounts' AND column_name='fa_funding_savings_account_id'")) {
                assertTrue(rows.next());assertEquals(1,rows.getLong(1));
            }
            try(var rows=sql.executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND ((table_name='branches' AND column_name='bra_branch_id') OR (table_name='customer_classification_types' AND column_name='cct_type_id')) AND data_type='bigint'")) {
                assertTrue(rows.next());assertEquals(2,rows.getLong(1));
            }
            try(var rows=sql.executeQuery("SELECT COUNT(*) FROM pg_constraint WHERE contype='f' AND confrelid IN ('public.branches'::regclass,'public.customer_classification_types'::regclass)")) {
                assertTrue(rows.next());assertEquals(4,rows.getLong(1));
            }
            try(var rows=sql.executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND column_name IN ('gcm_operator_user_id','lrs_operator_user_id','sc_operator_user_id') AND data_type='integer'")) {
                assertTrue(rows.next());assertEquals(3,rows.getLong(1));
            }
        }
    }
}
