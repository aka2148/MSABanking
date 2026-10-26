package com.bank.fdservice;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

final class CustomerProductAccess {
    static final String BLOCKED_RISK = "HIGH";
    private CustomerProductAccess() {}
    static void requireVerified(long userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Map<?, ?> details = authentication != null && authentication.getDetails() instanceof Map<?, ?> map ? map : Map.of();
        Object currentUserId = details.get("userId");
        if (!(currentUserId instanceof Number number) || number.longValue() != userId)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current customer identity is unavailable.");
        Object kycValue = details.get("kycStatus");
        Object riskValue = details.get("riskCategory");
        String kyc = kycValue == null ? "UNKNOWN" : String.valueOf(kycValue);
        String risk = riskValue == null ? "" : String.valueOf(riskValue);
        String message = !"VERIFIED".equalsIgnoreCase(kyc) ? kycMessage(kyc)
                : BLOCKED_RISK.equalsIgnoreCase(risk)
                ? "Your risk category needs bank officer review. You can't book fixed deposits, apply for loans or request forex. Please consult your bank officer."
                : null;
        if (message != null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
    private static String kycMessage(String status) {
        return "REJECTED".equalsIgnoreCase(status) ? "Your KYC verification was rejected. You can't book fixed deposits, apply for loans or request forex. Please consult your bank officer to resolve the rejection and complete the KYC process." : "Your KYC verification is still pending. You can't book fixed deposits, apply for loans or request forex until it is complete. Please consult your bank officer to complete the KYC process.";
    }
}
