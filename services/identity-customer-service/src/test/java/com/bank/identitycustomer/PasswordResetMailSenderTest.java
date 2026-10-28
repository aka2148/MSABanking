package com.bank.identitycustomer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PasswordResetMailSenderTest {
    private static final String API_KEY = "reset-test-api-key";
    private static final String CODE = "123456";
    private static final String URL = "https://api.brevo.com/v3/smtp/email";

    @Test
    void sendsTheBrevoPayloadWithAParsedSender() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", API_KEY))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(jsonPath("$.sender.name").value("MoneyPal Test"))
                .andExpect(jsonPath("$.sender.email").value("sender@example.test"))
                .andExpect(jsonPath("$.to[0].email").value("customer@example.test"))
                .andExpect(jsonPath("$.subject").value("MoneyPal password reset code"))
                .andExpect(jsonPath("$.textContent").value("Your MoneyPal password reset code is 123456. It expires in 10 minutes."))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        PasswordResetMailSender sender = sender(builder, mock(PasswordResetOtpInvalidator.class));
        Logger logger = (Logger) LoggerFactory.getLogger("org.springframework.web.client.DefaultRestClient");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            sender.sendCode(42L, "customer@example.test", CODE);
        } finally {
            logger.detachAppender(appender);
        }
        server.verify();
        String logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + b);
        assertFalse(logged.contains(CODE));
        assertFalse(logged.contains(API_KEY));
        assertFalse(logged.contains("customer@example.test"));
    }

    @Test
    void providerErrorBodyIsNotLoggedAndTheOtpIsBurned() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("provider echoed " + CODE + " and " + API_KEY));
        PasswordResetOtpInvalidator invalidator = mock(PasswordResetOtpInvalidator.class);
        Logger logger = (Logger) LoggerFactory.getLogger(PasswordResetMailSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            sender(builder, invalidator).sendCode(42L, "customer@example.test", CODE);
        } finally {
            logger.detachAppender(appender);
        }

        server.verify();
        verify(invalidator).burn(42L);
        String logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + b);
        assertTrue(logged.contains("Password reset email delivery failed."));
        assertFalse(logged.contains(CODE));
        assertFalse(logged.contains(API_KEY));
        assertFalse(logged.contains("customer@example.test"));
    }

    @Test
    void passwordChangeNoticeContainsNoResetMaterial() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andExpect(jsonPath("$.subject").value("Your MoneyPal password was changed"))
                .andExpect(jsonPath("$.textContent").value("Your MoneyPal password was changed. If you did not make this change, contact the bank."))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        sender(builder, mock(PasswordResetOtpInvalidator.class)).sendPasswordChanged("customer@example.test");
        server.verify();
    }

    private PasswordResetMailSender sender(RestClient.Builder builder, PasswordResetOtpInvalidator invalidator) {
        return new PasswordResetMailSender(builder, URL, API_KEY,
                "MoneyPal Test <sender@example.test>", invalidator);
    }
}
