package com.bank.fdservice;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.AfterEach;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerProductAccessTest {
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    private void session(String kyc, String risk) {
        var authentication = new UsernamePasswordAuthenticationToken("customer", null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        authentication.setDetails(Map.of("userId", 7L, "kycStatus", kyc == null ? "" : kyc,
                "riskCategory", risk == null ? "" : risk));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    @Test void verifiedLowAndMediumRiskCanProceed() throws Exception {
        for (String risk : new String[]{"LOW", "MEDIUM", null}) {
            session("VERIFIED", risk);
            assertDoesNotThrow(() -> CustomerProductAccess.requireVerified(7));
        }
    }
    @Test void pendingRejectedUnknownAndMissingSnapshotsFailClosed() throws Exception {
        for (String kyc : new String[]{"PENDING", "REJECTED", null}) {
            session(kyc, "LOW");
            var error = assertThrows(ResponseStatusException.class, () -> CustomerProductAccess.requireVerified(7));
            assertEquals(403,error.getStatusCode().value());
            assertTrue(error.getReason().contains("bank officer"));
        }
        SecurityContextHolder.clearContext();
        assertThrows(ResponseStatusException.class, () -> CustomerProductAccess.requireVerified(7));
    }
    @Test void highRiskRequiresOfficerReview() throws Exception {
        session("VERIFIED",CustomerProductAccess.BLOCKED_RISK);
        var error = assertThrows(ResponseStatusException.class, () -> CustomerProductAccess.requireVerified(7));
        assertTrue(error.getReason().contains("risk category"));
    }
}
