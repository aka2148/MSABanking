package com.bank.security.userservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
public class UserServiceClientConfig {
    @Bean
    RestClient userServiceRestClient(
            @Value("${app.user-service.base-url}") String baseUrl,
            @Value("${app.user-service.timeout-ms:2000}") int timeoutMs,
            @Value("${app.user-service.service-token}") String serviceToken) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .requestFactory(requestFactory)
                .build();
    }
}