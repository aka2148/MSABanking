package com.bank.identitycustomer;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.mockito.Mockito.*;

class AuthEmailVerificationTest {
    private static final String KEY = "test-brevo-key";
    private static final String EMAIL = "person@example.com";

    @Test
    void sendsBrevoPayloadAndParsesPlainAddress() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(EMAIL))).thenReturn(0);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", KEY))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(header("Accept", "application/json"))
                .andExpect(jsonPath("$.sender.name").value("MoneyPal"))
                .andExpect(jsonPath("$.sender.email").value("sender@example.com"))
                .andExpect(jsonPath("$.to[0].email").value(EMAIL))
                .andExpect(jsonPath("$.textContent").value(org.hamcrest.Matchers.containsString("verification code is")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        controller(jdbc, builder, "sender@example.com").sendEmailVerification(new AuthController.EmailOtpSendRequest(EMAIL));
        server.verify();
    }

    @Test
    void parsesNamedSender() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(EMAIL))).thenReturn(0);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(jsonPath("$.sender.name").value("Banking Lab"))
                .andExpect(jsonPath("$.sender.email").value("sender@example.com"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        controller(jdbc, builder, "Banking Lab <sender@example.com>").sendEmailVerification(new AuthController.EmailOtpSendRequest(EMAIL));
        server.verify();
    }

    @Test
    void providerFailureReturnsSafe503() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(EMAIL))).thenReturn(0);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"invalid request\"}"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller(jdbc, builder, "sender@example.com").sendEmailVerification(new AuthController.EmailOtpSendRequest(EMAIL)));
        assertEquals(503, error.getStatusCode().value());
        assertTrue(error.getReason().contains("could not be delivered"));
        assertFalse(error.getMessage().contains(KEY));
        server.verify();
    }

    private AuthController controller(JdbcTemplate jdbc, RestClient.Builder builder, String from) {
        return new AuthController(jdbc, mock(PasswordEncoder.class),
                Base64.getEncoder().encodeToString(new byte[64]), 60_000, mock(TotpService.class),
                new CustomerRiskConfiguration("LOW"), from,
                "https://api.brevo.com/v3/smtp/email", builder, KEY);
    }
}
