package com.bank.fd;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the new migration against isolated H2, not application datasource configuration. */
class SavingsAccountSchemaTest {
    @Test void migrationPreservesLegacyAccountsAndEnforcesBalanceOwnerAndCurrency() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "");
             Statement sql = db.createStatement()) {
            // H2 lacks PostgreSQL's shorthand, while preserving the same timestamp semantics.
            sql.execute("CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE");
            sql.execute("CREATE TABLE public.fd_user_ref(fur_user_id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE public.fd_currency_ref(fcr_currency_id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE public.fd_accounts(fa_account_id BIGINT PRIMARY KEY, fa_primary_holder_id BIGINT, fa_currency_id BIGINT)");
            sql.execute("INSERT INTO public.fd_user_ref VALUES (41), (42)");
            sql.execute("INSERT INTO public.fd_currency_ref VALUES (3), (4)");
            sql.execute("INSERT INTO public.fd_accounts VALUES (1,41,3)");
            try (var resource = getClass().getResourceAsStream("/db/migration/V26__savings_accounts_and_fd_source.sql")) {
                assertNotNull(resource);
                String migration = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
                for (String command : migration.split(";")) if (!command.isBlank()) sql.execute(command);
            }
            try (var resource = getClass().getResourceAsStream("/db/migration/V27__fd_double_entry_journals.sql")) {
                assertNotNull(resource);
                String migration = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
                for (String command : migration.split(";")) if (!command.isBlank()) sql.execute(command);
            }
            sql.execute("INSERT INTO public.savings_accounts(sa_account_number,sa_holder_id,sa_currency_id,sa_display_name,sa_created_date) VALUES ('SA1',41,3,'Savings',CURRENT_TIMESTAMP)");
            try (ResultSet rows=sql.executeQuery("SELECT fa_funding_savings_account_id FROM public.fd_accounts WHERE fa_account_id=1")) {
                assertTrue(rows.next()); assertNull(rows.getObject(1));
            }
            sql.execute("UPDATE public.fd_accounts SET fa_funding_savings_account_id=1 WHERE fa_account_id=1");
            assertThrows(SQLException.class, () -> sql.execute("UPDATE public.fd_accounts SET fa_primary_holder_id=42 WHERE fa_account_id=1"));
            assertThrows(SQLException.class, () -> sql.execute("UPDATE public.fd_accounts SET fa_currency_id=4 WHERE fa_account_id=1"));
            assertThrows(SQLException.class, () -> sql.execute("UPDATE public.savings_accounts SET sa_balance=-1 WHERE sa_account_id=1"));
            assertThrows(SQLException.class, () -> sql.execute("INSERT INTO public.fd_journals(fj_key,fj_fd_account_id,fj_savings_account_id,fj_currency_id,fj_operation,fj_posted_at,fj_total_debit,fj_total_credit) VALUES ('bad',1,1,3,'FUNDING',CURRENT_TIMESTAMP,100,90)"));
            sql.execute("INSERT INTO public.fd_journals(fj_key,fj_fd_account_id,fj_savings_account_id,fj_currency_id,fj_operation,fj_posted_at,fj_total_debit,fj_total_credit) VALUES ('good',1,1,3,'FUNDING',CURRENT_TIMESTAMP,100,100)");
            assertThrows(SQLException.class, () -> sql.execute("INSERT INTO public.fd_journals(fj_key,fj_fd_account_id,fj_savings_account_id,fj_currency_id,fj_operation,fj_posted_at,fj_total_debit,fj_total_credit) VALUES ('duplicate',1,1,3,'FUNDING',CURRENT_TIMESTAMP,100,100)"));
            try (ResultSet rows=sql.executeQuery("SELECT sa_balance FROM public.savings_accounts")) {
                assertTrue(rows.next()); assertEquals(0, rows.getBigDecimal(1).signum());
            }
        }
    }
}
