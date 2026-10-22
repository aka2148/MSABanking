package com.bank.fdservice;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.web.server.ResponseStatusException;
import java.sql.ResultSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerProductAccessTest {
    private JdbcTemplate reference(String kyc, String risk, boolean exists) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(exists);
        when(rs.getString(1)).thenReturn(kyc);
        when(rs.getString(2)).thenReturn(risk);
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(7L)))
            .thenAnswer(invocation -> ((ResultSetExtractor<?>)invocation.getArgument(1)).extractData(rs));
        return jdbc;
    }
    @Test void verifiedLowAndMediumRiskCanProceed() throws Exception {
        for (String risk : new String[]{"LOW", "MEDIUM", null}) assertDoesNotThrow(() -> CustomerProductAccess.requireVerified(reference("VERIFIED",risk,true),7));
    }
    @Test void pendingRejectedUnknownAndMissingSnapshotsFailClosed() throws Exception {
        for (String kyc : new String[]{"PENDING", "REJECTED", null}) {
            var jdbc = reference(kyc,"LOW",true);
            var error = assertThrows(ResponseStatusException.class, () -> CustomerProductAccess.requireVerified(jdbc,7));
            assertEquals(403,error.getStatusCode().value());
            assertTrue(error.getReason().contains("bank officer"));
        }
        var missing = reference(null,null,false);
        assertThrows(ResponseStatusException.class, () -> CustomerProductAccess.requireVerified(missing,7));
    }
    @Test void highRiskRequiresOfficerReview() throws Exception {
        var jdbc = reference("VERIFIED",CustomerProductAccess.BLOCKED_RISK,true);
        var error = assertThrows(ResponseStatusException.class, () -> CustomerProductAccess.requireVerified(jdbc,7));
        assertTrue(error.getReason().contains("risk category"));
    }
}
