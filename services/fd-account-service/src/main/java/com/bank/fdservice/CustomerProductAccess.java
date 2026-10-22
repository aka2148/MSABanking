package com.bank.fdservice;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

final class CustomerProductAccess {
    static final String BLOCKED_RISK = "HIGH";
    private CustomerProductAccess() {}
    static void requireVerified(JdbcTemplate jdbc, long userId) {
        String message = jdbc.query("SELECT fur_kyc_status,fur_risk_category FROM fd_user_ref WHERE fur_user_id=?", rs -> {
            if (!rs.next()) return kycMessage("UNKNOWN");
            String kyc = rs.getString(1);
            if (!"VERIFIED".equalsIgnoreCase(kyc)) return kycMessage(kyc == null ? "UNKNOWN" : kyc);
            if (BLOCKED_RISK.equalsIgnoreCase(rs.getString(2))) return "Your risk category needs bank officer review. You can't book fixed deposits, apply for loans or request forex. Please consult your bank officer.";
            return null;
        }, userId);
        if (message != null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
    private static String kycMessage(String status) {
        return "REJECTED".equalsIgnoreCase(status) ? "Your KYC verification was rejected. You can't book fixed deposits, apply for loans or request forex. Please consult your bank officer to resolve the rejection and complete the KYC process." : "Your KYC verification is still pending. You can't book fixed deposits, apply for loans or request forex until it is complete. Please consult your bank officer to complete the KYC process.";
    }
}
