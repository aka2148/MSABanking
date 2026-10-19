package com.bank.productservice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration @EnableMethodSecurity
public class SecurityConfiguration {
    private final JwtFilter jwtFilter;
    public SecurityConfiguration(JwtFilter jwtFilter){this.jwtFilter=jwtFilter;}
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http)throws Exception{
        return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a->a.requestMatchers("/actuator/health","/actuator/info").permitAll()
                        .requestMatchers("/api/v1/product-configurations", "/api/v1/product-configurations/**").hasAnyAuthority("ROLE_BANK_OFFICER","ROLE_ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/product/**","/api/v1/currencies/**","/api/v1/product-categories/**","/api/v1/fd-products/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(b->b.disable()).formLogin(f->f.disable())
                .addFilterBefore(jwtFilter,UsernamePasswordAuthenticationFilter.class).build();
    }
}
