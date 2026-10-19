package com.bank.fdservice;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration @EnableMethodSecurity public class SecurityConfiguration {
  private final JwtFilter filter; public SecurityConfiguration(JwtFilter filter){this.filter=filter;}
  @Bean SecurityFilterChain chain(HttpSecurity http)throws Exception{return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.requestMatchers("/actuator/health","/actuator/info","/error").permitAll().anyRequest().authenticated()).httpBasic(b->b.disable()).formLogin(f->f.disable()).addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();}
}
