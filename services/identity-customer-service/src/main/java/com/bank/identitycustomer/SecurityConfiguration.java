package com.bank.identitycustomer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    private final JwtAuthenticationFilter jwtFilter;
    public SecurityConfiguration(JwtAuthenticationFilter jwtFilter) { this.jwtFilter = jwtFilter; }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health", "/actuator/info", "/error", "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/login/**", "/api/v1/auth/email/verification/**", "/auth/password/forgot", "/auth/password/reset").permitAll().anyRequest().authenticated())
                .httpBasic(b -> b.disable()).formLogin(f -> f.disable())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class).build();
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
