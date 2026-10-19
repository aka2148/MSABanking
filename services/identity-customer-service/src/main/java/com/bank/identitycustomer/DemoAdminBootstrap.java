package com.bank.identitycustomer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Creates a local demo administrator from ignored environment configuration, without public role escalation. */
@Component
public class DemoAdminBootstrap implements ApplicationRunner {
    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final String username;
    private final String password;

    public DemoAdminBootstrap(
            JdbcTemplate jdbc,
            PasswordEncoder passwords,
            @Value("${app.demo-admin.username:}") String username,
            @Value("${app.demo-admin.password:}") String password) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if ((username == null || username.isBlank()) && (password == null || password.isBlank())) return;
        if (username == null || username.trim().length() < 3 || username.trim().length() > 255
                || password == null || password.length() < 20 || password.length() > 100) {
            throw new IllegalStateException("Demo admin bootstrap requires a username and a password of 20 to 100 characters.");
        }

        List<Long> existingUsers = jdbc.query(
                "SELECT ucr_user_id FROM user_credentials WHERE ucr_username=?",
                (rs, row) -> rs.getLong(1), username.trim());
        if (!existingUsers.isEmpty()) {
            List<String> existingRoles = jdbc.queryForList(
                    "SELECT r.rol_role_code FROM roles r JOIN user_roles ur ON ur.ur_role_id=r.rol_role_id WHERE ur.ur_user_id=?",
                    String.class, existingUsers.get(0));
            if (existingRoles.contains(ADMIN_ROLE)) return;
            throw new IllegalStateException("Configured demo admin username already belongs to a non-admin account.");
        }

        jdbc.update("INSERT INTO users(usr_user_type,usr_status) VALUES('EMPLOYEE','ACTIVE')");
        long userId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO user_credentials(ucr_user_id,ucr_username,ucr_password_hash,ucr_password_changed_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",
                userId, username.trim(), passwords.encode(password));
        jdbc.update("INSERT INTO roles(rol_role_code,rol_role_name,rol_description) VALUES(?,?,?) " +
                        "ON DUPLICATE KEY UPDATE rol_role_name=VALUES(rol_role_name)",
                ADMIN_ROLE, "Administrator", "Local demo administrator");
        long roleId = jdbc.queryForObject("SELECT rol_role_id FROM roles WHERE rol_role_code=?", Long.class, ADMIN_ROLE);
        jdbc.update("INSERT INTO user_roles(ur_user_id,ur_role_id) VALUES(?,?)", userId, roleId);
    }
}
