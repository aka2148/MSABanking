package com.bank.fdservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class IdentitySessionClient {
    private final RestClient client;

    public IdentitySessionClient(RestClient.Builder builder,
                                 @Value("${app.identity.base-url:http://localhost:8081}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public Session validate(String token) {
        try {
            Session session = client.get().uri("/api/v1/auth/session")
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve().body(Session.class);
            if (session == null || session.userId() <= 0 || session.username() == null || session.username().isBlank()
                    || session.roles() == null) throw new IdentityServiceUnavailableException();
            return session;
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()
                    || ex.getStatusCode().value() == HttpStatus.FORBIDDEN.value())
                throw new InvalidIdentitySessionException();
            throw new IdentityServiceUnavailableException();
        } catch (RestClientException ex) {
            throw new IdentityServiceUnavailableException();
        }
    }

    public record Session(long userId, String username, List<String> roles, String kycStatus, String riskCategory) {}

    public static final class InvalidIdentitySessionException extends RuntimeException {}
    public static final class IdentityServiceUnavailableException extends RuntimeException {}
}
