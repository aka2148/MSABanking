package com.bank.security.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Opt-in, environment-driven bootstrap; disabled unless APP_STAFF_BOOTSTRAP_ENABLED=true. */
@Component
@ConditionalOnProperty(prefix = "app.staff-bootstrap", name = "enabled", havingValue = "true")
public class StaffBootstrapRunner implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(StaffBootstrapRunner.class);

    private final StaffBootstrapService service;
    private final String username;
    private final String password;
    private final String fullName;
    private final String email;

    public StaffBootstrapRunner(StaffBootstrapService service,
                                @Value("${app.staff-bootstrap.username:}") String username,
                                @Value("${app.staff-bootstrap.password:}") String password,
                                @Value("${app.staff-bootstrap.full-name:}") String fullName,
                                @Value("${app.staff-bootstrap.email:}") String email) {
        this.service = service;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
    }

    @Override
    public void run(ApplicationArguments args) {
        StaffBootstrapService.BootstrapResult result = service.createBankOfficer(username, password, fullName, email);
        if (result.created()) {
            logger.info("Environment-gated BANK_OFFICER bootstrap completed; password omitted.");
        } else {
            logger.info("Environment-gated BANK_OFFICER bootstrap found the account already provisioned; no changes made.");
        }
    }
}
