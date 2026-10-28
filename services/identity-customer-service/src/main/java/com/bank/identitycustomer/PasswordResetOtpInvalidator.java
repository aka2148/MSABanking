package com.bank.identitycustomer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class PasswordResetOtpInvalidator {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate requiresNew;

    PasswordResetOtpInvalidator(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    void burn(long otpId) {
        requiresNew.executeWithoutResult(status -> jdbc.update(
                "UPDATE password_reset_otps SET pro_consumed_at=UTC_TIMESTAMP(6) "
                        + "WHERE pro_id=? AND pro_consumed_at IS NULL", otpId));
    }
}
