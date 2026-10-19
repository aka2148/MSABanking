package com.bank.productservice;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private final byte[] key;
    public JwtFilter(@Value("${app.jwt.secret}") String secret) { key=Decoders.BASE64.decode(secret); }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String header=req.getHeader("Authorization");
        if(header!=null&&header.startsWith("Bearer ")) try {
            Claims claims=Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(key)).build().parseClaimsJws(header.substring(7)).getBody();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(claims.getSubject(),null,authorities(claims.get("roles"))));
        } catch(RuntimeException ignored) {
            SecurityContextHolder.clearContext();
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired bearer token");
            return;
        }
        chain.doFilter(req,res);
    }

    private List<SimpleGrantedAuthority> authorities(Object claim) {
        Stream<String> values;
        if (claim instanceof Collection<?> roles) {
            values = roles.stream().filter(String.class::isInstance).map(String.class::cast);
        } else if (claim instanceof String roles) {
            values = Arrays.stream(roles.split(","));
        } else {
            return List.of();
        }
        return values.map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .map(value -> value.startsWith("ROLE_") ? value : "ROLE_" + value)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
