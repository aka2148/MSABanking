package com.bank.identitycustomer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/** Brevo sender for password recovery. Provider bodies are intentionally never logged. */
@Component
class PasswordResetMailSender {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailSender.class);

    private final RestClient mailApi;
    private final String mailApiUrl;
    private final String mailApiKey;
    private final String from;
    private final PasswordResetOtpInvalidator otpInvalidator;

    PasswordResetMailSender(RestClient.Builder mailApiBuilder,
            @Value("${app.auth.password-reset-api-url:https://api.brevo.com/v3/smtp/email}") String mailApiUrl,
            @Value("${app.auth.password-reset-api-key:}") String mailApiKey,
            @Value("${app.auth.password-reset-from:no-reply@localhost}") String from,
            PasswordResetOtpInvalidator otpInvalidator) {
        this.mailApi = mailApiBuilder.build();
        this.mailApiUrl = mailApiUrl;
        this.mailApiKey = mailApiKey;
        this.from = from;
        this.otpInvalidator = otpInvalidator;
    }

    void sendCode(long otpId, String email, String code) {
        try {
            send(email, "MoneyPal password reset code",
                    "Your MoneyPal password reset code is " + code + ". It expires in 10 minutes.");
        } catch (RuntimeException ex) {
            try {
                otpInvalidator.burn(otpId);
            } catch (RuntimeException ignored) {
                log.warn("Password reset email delivery failed; the code could not be retained safely.");
                return;
            }
            log.warn("Password reset email delivery failed.");
        }
    }

    void sendPasswordChanged(String email) {
        try {
            send(email, "Your MoneyPal password was changed",
                    "Your MoneyPal password was changed. If you did not make this change, contact the bank.");
        } catch (RuntimeException ex) {
            log.warn("Password change notification email delivery failed.");
        }
    }

    private void send(String email, String subject, String text) {
        Sender sender = parseSender(from);
        mailApi.post().uri(mailApiUrl).header("api-key", mailApiKey)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .body(new BrevoRequest(sender, List.of(new Recipient(email)), subject, text))
                .retrieve().onStatus(HttpStatusCode::isError, (request, response) -> {
                    // Provider response bodies can echo recipient data, OTPs, or credentials; omit them.
                    throw new IllegalStateException("Password reset email provider rejected the request");
                }).toBodilessEntity();
    }

    private Sender parseSender(String configured) {
        String value = configured == null ? "" : configured.trim();
        int open = value.lastIndexOf('<');
        int close = value.endsWith(">") ? value.length() - 1 : -1;
        if (open >= 0 && close > open) {
            String name = value.substring(0, open).trim().replaceAll("^\\\"|\\\"$", "").trim();
            return new Sender(name.isBlank() ? "MoneyPal" : name, value.substring(open + 1, close).trim());
        }
        return new Sender("MoneyPal", value);
    }

    private record Sender(String name, String email) {}
    private record Recipient(String email) {}
    private record BrevoRequest(Sender sender, List<Recipient> to, String subject, String textContent) {
        @Override public String toString() { return "BrevoRequest[redacted]"; }
    }
}
