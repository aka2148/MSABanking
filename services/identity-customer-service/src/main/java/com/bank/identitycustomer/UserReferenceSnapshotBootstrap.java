package com.bank.identitycustomer;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class UserReferenceSnapshotBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public UserReferenceSnapshotBootstrap(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Long userId : jdbc.queryForList("SELECT cus_user_id FROM customers ORDER BY cus_user_id", Long.class)) {
            UserSnapshots.publishInitialUserSnapshot(jdbc, userId);
        }
    }
}
