package com.bank.fdservice;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@Component
public class MpinProofVerifier {
    private static final Logger log=LoggerFactory.getLogger(MpinProofVerifier.class);
    private final byte[] key;private final boolean required;private final JdbcTemplate jdbc;
    public MpinProofVerifier(@Value("${app.jwt.secret}")String secret,@Value("${app.mpin.required:true}")boolean required,JdbcTemplate jdbc){this.key=hmac(Decoders.BASE64.decode(secret),"mpin-proof-v1");this.required=required;this.jdbc=jdbc;if(!required)log.warn("APP_MPIN_REQUIRED is false: MPIN proof enforcement is disabled; keep this enabled outside rollback testing.");}
    public String verify(Authentication actor,String token,String purpose,long ownerId,String canonicalBinding){
        if(!required)return null;if(token==null||token.isBlank())throw new MpinProofException("MPIN_REQUIRED",HttpStatus.FORBIDDEN);
        try{Claims c=Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(key)).setAllowedClockSkewSeconds(5).build().parseClaimsJws(token).getBody();
            if(!"mpin-proof".equals(c.get("typ",String.class))||!"identity".equals(c.getIssuer())||!purpose.equals(c.get("purpose",String.class))||!Long.toString(ownerId).equals(c.getSubject())||actor==null||!actor.getName().equals(c.get("act",String.class))||!MessageDigest.isEqual(hash(canonicalBinding).getBytes(StandardCharsets.US_ASCII),String.valueOf(c.get("bind")).getBytes(StandardCharsets.US_ASCII)))throw new IllegalArgumentException();
            String jti=c.get("jti",String.class);if(jti==null||jti.length()>36)throw new IllegalArgumentException();return jti;
        }catch(RuntimeException e){throw new MpinProofException("MPIN_PROOF_INVALID",HttpStatus.FORBIDDEN);}
    }
    public void consume(String jti,long userId,String purpose){if(!required)return;try{jdbc.update("INSERT INTO fd_mpin_proof_use(jti,user_id,purpose) VALUES(?,?,?)",jti,userId,purpose);}catch(DuplicateKeyException e){throw new MpinProofException("MPIN_PROOF_INVALID",HttpStatus.CONFLICT);}}
    public static String canonical(String purpose,Object...fields){StringBuilder s=new StringBuilder(purpose);for(Object f:fields){if(f==null)throw new IllegalArgumentException("Missing MPIN binding");String v=f.toString();if(f instanceof java.math.BigDecimal b)v=b.stripTrailingZeros().toPlainString();else if(f instanceof Number)v=new java.math.BigDecimal(v).stripTrailingZeros().toPlainString();else if(f instanceof String)v=v.trim();s.append('|').append(v);}return s.toString();}
    public static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static byte[] hmac(byte[]k,String v){try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(k,"HmacSHA256"));return m.doFinal(v.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
    public static class MpinProofException extends RuntimeException{private final String code;private final HttpStatus status;MpinProofException(String c,HttpStatus s){super(c);code=c;status=s;}public String code(){return code;}public HttpStatus status(){return status;}}
}
