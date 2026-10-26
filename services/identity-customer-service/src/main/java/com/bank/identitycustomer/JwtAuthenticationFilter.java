package com.bank.identitycustomer;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final byte[] key;
    private final JdbcTemplate jdbc;
    public JwtAuthenticationFilter(@Value("${app.jwt.secret}") String secret, JdbcTemplate jdbc) { key = Decoders.BASE64.decode(secret); this.jdbc=jdbc; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header=request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims=Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(key)).build().parseClaimsJws(header.substring(7)).getBody();
                Number version=claims.get("tokenVersion",Number.class);
                Number tokenUserId=claims.get("userId",Number.class);
                var current=jdbc.query("SELECT c.ucr_user_id,c.ucr_token_version,u.usr_status FROM user_credentials c JOIN users u ON u.usr_user_id=c.ucr_user_id WHERE c.ucr_username=?",rs->rs.next()?new Object[]{rs.getLong(1),rs.getInt(2),rs.getString(3)}:null,claims.getSubject());
                if(current==null || !"ACTIVE".equals(current[2]) || tokenUserId==null || tokenUserId.longValue()!=((Number)current[0]).longValue()
                        || version==null || version.intValue()!=((Number)current[1]).intValue()) throw new IllegalArgumentException("Revoked token");
                String roles=jdbc.query("SELECT GROUP_CONCAT(r.rol_role_code ORDER BY r.rol_role_code SEPARATOR ',') FROM user_roles ur JOIN roles r ON r.rol_role_id=ur.ur_role_id JOIN user_credentials c ON c.ucr_user_id=ur.ur_user_id WHERE c.ucr_username=?",rs->rs.next()?rs.getString(1):null,claims.getSubject());
                var authorities=roles==null?java.util.List.<SimpleGrantedAuthority>of():Arrays.stream(roles.split(",")).map(String::trim).filter(s->!s.isBlank()).map(SimpleGrantedAuthority::new).toList();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(claims.getSubject(),null,authorities));
            } catch (RuntimeException ignored) { SecurityContextHolder.clearContext(); }
        }
        chain.doFilter(request,response);
    }
}
