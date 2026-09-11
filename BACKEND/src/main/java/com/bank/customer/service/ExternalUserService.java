package com.bank.customer.service;

import com.bank.common.exception.ResourceNotFoundException;
import com.bank.common.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.client.RestClient;
import java.util.Map;

@Service
public class ExternalUserService {
    private final RestClient client;
    public ExternalUserService(@Value("${app.user-service.base-url}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }
    public ResponseEntity<Map<String,Object>> getUser(Long id, String authorization) {
        try {
            ResponseEntity<Map> response = client.get().uri("/users/{id}", id)
                    .header(HttpHeaders.AUTHORIZATION, authorization == null ? "" : authorization)
                    .retrieve().toEntity(Map.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("User not found with ID: " + id);
        } catch (RestClientResponseException ex) {
            throw new ServiceUnavailableException("User Service returned HTTP " + ex.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            throw new ServiceUnavailableException("User Service is unavailable");
        }
    }
}
