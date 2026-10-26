package com.bank.fdservice;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IdentitySessionClientTest {
    @Test void usesIdentityAsTheCurrentSessionAuthority() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://identity.test/api/v1/auth/session"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer synthetic-token"))
                .andRespond(withSuccess("{\"userId\":7,\"username\":\"customer\",\"roles\":[\"ROLE_CUSTOMER\"],\"kycStatus\":\"VERIFIED\",\"riskCategory\":\"LOW\"}", MediaType.APPLICATION_JSON));

        var session = new IdentitySessionClient(builder, "http://identity.test").validate("synthetic-token");
        assertEquals(7L, session.userId());
        assertEquals("VERIFIED", session.kycStatus());
        server.verify();
    }

    @Test void revokedTokenAndUnavailableIdentityFailClosed() {
        RestClient.Builder rejectedBuilder = RestClient.builder();
        MockRestServiceServer rejected = MockRestServiceServer.bindTo(rejectedBuilder).build();
        rejected.expect(requestTo("http://identity.test/api/v1/auth/session"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED));
        assertThrows(IdentitySessionClient.InvalidIdentitySessionException.class,
                () -> new IdentitySessionClient(rejectedBuilder, "http://identity.test").validate("revoked-token"));
        rejected.verify();

        RestClient.Builder unavailableBuilder = RestClient.builder();
        MockRestServiceServer unavailable = MockRestServiceServer.bindTo(unavailableBuilder).build();
        unavailable.expect(requestTo("http://identity.test/api/v1/auth/session"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(IdentitySessionClient.IdentityServiceUnavailableException.class,
                () -> new IdentitySessionClient(unavailableBuilder, "http://identity.test").validate("active-token"));
        unavailable.verify();
    }
}
