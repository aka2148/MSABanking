package com.bank.fdservice;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private final IdentitySessionClient sessions;

    public JwtFilter(IdentitySessionClient sessions) { this.sessions = sessions; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                IdentitySessionClient.Session session = sessions.validate(header.substring(7));
                var authorities = session.roles().stream().filter(role -> role != null && !role.isBlank())
                        .map(role -> role.trim().toUpperCase(Locale.ROOT))
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        .distinct().map(SimpleGrantedAuthority::new).toList();
                var authentication = new UsernamePasswordAuthenticationToken(session.username(), null, authorities);
                var details = new HashMap<String, Object>();
                details.put("userId", session.userId());
                details.put("kycStatus", session.kycStatus() == null ? "" : session.kycStatus());
                details.put("riskCategory", session.riskCategory() == null ? "" : session.riskCategory());
                authentication.setDetails(details);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (IdentitySessionClient.InvalidIdentitySessionException ex) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid, expired, or revoked bearer token");
                return;
            } catch (IdentitySessionClient.IdentityServiceUnavailableException ex) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Identity service is unavailable");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
