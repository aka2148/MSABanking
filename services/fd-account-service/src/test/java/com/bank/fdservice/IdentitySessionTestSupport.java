package com.bank.fdservice;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

final class IdentitySessionTestSupport {
    private IdentitySessionTestSupport() {}

    static void stub(IdentitySessionClient client, String jwtSecret) {
        byte[] key = Decoders.BASE64.decode(jwtSecret);
        when(client.validate(anyString())).thenAnswer(invocation -> {
            try {
                Claims claims = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(key)).build()
                        .parseClaimsJws(invocation.getArgument(0)).getBody();
                Object roleClaim = claims.get("roles");
                List<String> roles = roleClaim instanceof String roleText
                        ? Arrays.stream(roleText.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList()
                        : List.of();
                Number userId = claims.get("userId", Number.class);
                long id = userId == null ? 1L : userId.longValue();
                return new IdentitySessionClient.Session(id, claims.getSubject(), roles, "VERIFIED", "LOW");
            } catch (RuntimeException invalid) {
                throw new IdentitySessionClient.InvalidIdentitySessionException();
            }
        });
    }
}
