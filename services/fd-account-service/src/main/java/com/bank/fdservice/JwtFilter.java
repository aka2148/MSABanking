package com.bank.fdservice;
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
@Component public class JwtFilter extends OncePerRequestFilter {
  private final byte[] key;
  public JwtFilter(@Value("${app.jwt.secret}") String secret){key=Decoders.BASE64.decode(secret);}
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
    String h=req.getHeader("Authorization");if(h!=null&&h.startsWith("Bearer "))try{Claims c=Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(key)).build().parseClaimsJws(h.substring(7)).getBody();String r=c.get("roles",String.class);var a=r==null?java.util.List.<SimpleGrantedAuthority>of():Arrays.stream(r.split(",")).map(String::trim).filter(v->!v.isBlank()).map(SimpleGrantedAuthority::new).toList();var auth=new UsernamePasswordAuthenticationToken(c.getSubject(),null,a);auth.setDetails(java.util.Map.of("userId",c.get("userId",Number.class).longValue()));SecurityContextHolder.getContext().setAuthentication(auth);}catch(RuntimeException ignored){SecurityContextHolder.clearContext();}chain.doFilter(req,res);
  }
}
