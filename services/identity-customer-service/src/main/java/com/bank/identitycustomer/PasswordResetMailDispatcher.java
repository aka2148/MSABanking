package com.bank.identitycustomer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
class PasswordResetMailDispatcher {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailDispatcher.class);
    private final TaskExecutor executor;
    private final PasswordResetMailSender sender;
    private final PasswordResetOtpInvalidator otpInvalidator;

    PasswordResetMailDispatcher(@Qualifier("passwordResetMailExecutor") TaskExecutor executor,
            PasswordResetMailSender sender, PasswordResetOtpInvalidator otpInvalidator) {
        this.executor = executor;
        this.sender = sender;
        this.otpInvalidator = otpInvalidator;
    }

    void sendCode(long otpId, String email, String code) {
        try {
            executor.execute(() -> sender.sendCode(otpId, email, code));
        } catch (RuntimeException ex) {
            try {
                otpInvalidator.burn(otpId);
            } catch (RuntimeException ignored) {
                log.warn("Password reset email queue is unavailable; the code could not be retained safely.");
                return;
            }
            log.warn("Password reset email queue is full; delivery was skipped.");
        }
    }

    void sendPasswordChanged(String email) {
        try {
            executor.execute(() -> sender.sendPasswordChanged(email));
        } catch (RuntimeException ex) {
            log.warn("Password change notification email queue is unavailable.");
        }
    }
}
