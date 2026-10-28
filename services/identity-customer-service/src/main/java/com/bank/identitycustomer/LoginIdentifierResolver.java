package com.bank.identitycustomer;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/** Shared login and password-recovery lookup for usernames and primary mobile numbers. */
final class LoginIdentifierResolver {
    private static final String CREDENTIAL_COLUMNS = "SELECT u.usr_user_id,u.usr_status,c.ucr_password_hash,"
            + "c.ucr_user_id,c.ucr_username,c.ucr_mfa_enabled,c.ucr_mfa_secret,c.ucr_token_version,"
            + "(c.ucr_locked_until IS NOT NULL AND c.ucr_locked_until>UTC_TIMESTAMP(6)) AS is_locked ";

    private LoginIdentifierResolver() {}

    static List<Map<String, Object>> resolve(JdbcTemplate jdbc, String identifier, boolean lockForUpdate) {
        String lock = lockForUpdate ? " FOR UPDATE" : "";
        List<Map<String, Object>> usernameMatches = jdbc.queryForList(CREDENTIAL_COLUMNS
                + "FROM user_credentials c JOIN users u ON u.usr_user_id=c.ucr_user_id "
                + "WHERE c.ucr_username=?" + lock, identifier);
        if (!usernameMatches.isEmpty()) return usernameMatches;

        if (identifier == null || !identifier.matches("[0-9]{10}")) return List.of();
        List<Map<String, Object>> mobileMatches = jdbc.queryForList("SELECT DISTINCT u.usr_user_id,u.usr_status,"
                + "c.ucr_password_hash,c.ucr_user_id,c.ucr_username,c.ucr_mfa_enabled,c.ucr_mfa_secret,"
                + "c.ucr_token_version,(c.ucr_locked_until IS NOT NULL AND c.ucr_locked_until>UTC_TIMESTAMP(6)) AS is_locked "
                + "FROM user_credentials c JOIN users u ON u.usr_user_id=c.ucr_user_id "
                + "JOIN customers cu ON cu.cus_user_id=u.usr_user_id "
                + "JOIN customer_phone_numbers p ON p.cph_customer_id=cu.cus_customer_id "
                + "WHERE p.cph_phone_number=? AND p.cph_is_primary=1 ORDER BY c.ucr_user_id LIMIT 2" + lock,
                identifier);
        return mobileMatches.size() == 1 ? mobileMatches : List.of();
    }
}
