package com.bank.identitycustomer;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MpinService {
    private static final long PROOF_SECONDS=90;
    private static final Map<String,List<String>> FIELDS=Map.of(
            "TRANSFER",List.of("fromAccountId","toAccountNumber","amount"),
            "WITHDRAWAL",List.of("accountId","amount"),
            "FD_FUNDING",List.of("fdId","principal"),
            "FD_CLOSE",List.of("fdId"),
            "FD_PREMATURE_REQUEST",List.of("fdId"),
            "LOAN_APPLICATION",List.of("productId","amount","currencyCode","tenureMonths","destinationSavingsAccountId"),
            "FOREX_REQUEST",List.of("productId","amount","fundingCurrencyCode","destinationCurrencyCode"));
    private final JdbcTemplate jdbc;
    private final PasswordEncoder bcrypt=new BCryptPasswordEncoder(12);
    private final byte[] pepper;
    private final byte[] atRestKey;
    private final byte[] proofKey;
    private final int maxAttempts;
    private final int lockMinutes;
    private final SecureRandom random=new SecureRandom();

    public MpinService(JdbcTemplate jdbc,@Value("${app.jwt.secret}") String jwtSecret,
                       @Value("${app.security.mfa-encryption-key}") String encryptionSecret,
                       @Value("${app.mpin.pepper:}") String pepperOverride,
                       @Value("${app.mpin.max-attempts:5}") int maxAttempts,
                       @Value("${app.mpin.lock-minutes:30}") int lockMinutes) {
        this.jdbc=jdbc; this.maxAttempts=maxAttempts; this.lockMinutes=lockMinutes;
        byte[] enc=encryptionSecret.getBytes(StandardCharsets.UTF_8);
        this.pepper=pepperOverride==null||pepperOverride.isBlank()?hmac(enc,"mpin-pepper-v1"):pepperOverride.getBytes(StandardCharsets.UTF_8);
        this.atRestKey=hmac(enc,"mpin-at-rest-v1");
        this.proofKey=hmac(Decoders.BASE64.decode(jwtSecret),"mpin-proof-v1");
        if(maxAttempts<1||lockMinutes<1) throw new IllegalArgumentException("MPIN lockout settings must be positive");
    }

    public record Attempt(String code,int attemptsRemaining,Instant lockedUntil) { public boolean ok(){return "OK".equals(code);} }
    public record Proof(String proofToken,Instant expiresAt) {}
    public record Status(boolean set,boolean locked,Instant lockedUntil) {}
    public record Verification(Map<String,Object> fields,String proofToken,Instant expiresAt) {}

    @Transactional(readOnly=true)
    public Status status(long userId) {
        return jdbc.query("SELECT cmp_locked_until FROM customer_mpin WHERE cmp_user_id=?",rs->{if(!rs.next())return new Status(false,false,null);java.sql.Timestamp t=rs.getTimestamp(1);Instant until=t==null?null:t.toInstant();return new Status(true,until!=null&&until.isAfter(Instant.now()),until);},userId);
    }

    @Transactional
    public void setup(long userId,String username,String password,String pin,PasswordEncoder passwords) {
        String hash=jdbc.query("SELECT ucr_password_hash FROM user_credentials WHERE ucr_user_id=?",rs->rs.next()?rs.getString(1):null,userId);
        if(hash==null||!passwords.matches(password,hash))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Account password is incorrect.");
        validatePin(pin);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM customer_mpin WHERE cmp_user_id=?",Integer.class,userId)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"An MPIN is already set. Use change MPIN.");
        jdbc.update("INSERT INTO customer_mpin(cmp_user_id,cmp_hash,cmp_set_at) VALUES(?,?,UTC_TIMESTAMP(6))",userId,protect(bcrypt.encode(HexFormat.of().formatHex(digest(userId,pin)))));
        event(userId,"SET",username,null);
    }

    @Transactional
    public Attempt change(long userId,String username,String current,String replacement) {
        Attempt verified=checkLockedRow(userId,username,current,"MPIN_CHANGE"); if(!verified.ok())return verified;
        if(MessageDigest.isEqual(digest(userId,current),digest(userId,replacement)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"The new MPIN must differ from the current MPIN.");
        validatePin(replacement);
        jdbc.update("UPDATE customer_mpin SET cmp_hash=?,cmp_set_at=UTC_TIMESTAMP(6),cmp_failed_attempts=0,cmp_locked_until=NULL,cmp_last_failed_at=NULL,cmp_version=cmp_version+1 WHERE cmp_user_id=?",protect(bcrypt.encode(HexFormat.of().formatHex(digest(userId,replacement)))),userId);
        event(userId,"CHANGED",username,null); return new Attempt("OK",maxAttempts,null);
    }

    @Transactional
    public Attempt verify(long userId,String username,String pin,String purpose) {
        Attempt attempt=checkLockedRow(userId,username,pin,purpose);
        if(attempt.ok()) event(userId,"VERIFY_OK",username,purpose);
        return attempt;
    }

    @Transactional
    public Verification verifyAndIssue(long userId,String username,String pin,String purpose,Map<String,Object> fields) {
        List<String> expected=FIELDS.get(purpose);
        if(expected==null||fields==null||!fields.keySet().equals(Set.copyOf(expected))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown MPIN purpose or fields.");
        Attempt result=checkLockedRow(userId,username,pin,purpose);
        if(!result.ok())return new Verification(Map.of("code",result.code(),"attemptsRemaining",result.attemptsRemaining(),"lockedUntil",result.lockedUntil()==null?"":result.lockedUntil().toString()),null,null);
        event(userId,"VERIFY_OK",username,purpose);
        String binding=binding(purpose,fields); Instant now=Instant.now(), expires=now.plusSeconds(PROOF_SECONDS);
        String token=Jwts.builder().setHeaderParam("typ","JWT").setIssuer("identity").setSubject(Long.toString(userId))
                .claim("typ","mpin-proof").claim("act",username).claim("purpose",purpose).claim("bind",hashBinding(binding)).claim("jti",UUID.randomUUID().toString())
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(expires)).signWith(Keys.hmacShaKeyFor(proofKey),SignatureAlgorithm.HS256).compact();
        return new Verification(Map.of(),token,expires);
    }

    @Transactional
    public Attempt rekycVerify(long userId,String username,String pin) { return verify(userId,username,pin,"REKYC_REQUEST"); }

    @Transactional
    public void reset(long userId,String staff) {
        jdbc.update("DELETE FROM customer_mpin WHERE cmp_user_id=?",userId); event(userId,"RESET_BY_STAFF",staff,null);
    }

    @Transactional
    public Verification staffVerify(long userId,String staff,String pin,Map<String,Object> fields) {
        List<String> expected=FIELDS.get("WITHDRAWAL"); if(!fields.keySet().equals(Set.copyOf(expected)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown MPIN fields.");
        Attempt result=checkLockedRow(userId,staff,pin,"WITHDRAWAL");
        if(!result.ok()) return new Verification(Map.of("code",result.code(),"attemptsRemaining",result.attemptsRemaining(),"lockedUntil",result.lockedUntil()==null?"":result.lockedUntil().toString()),null,null);
        event(userId,"VERIFY_OK",staff,"WITHDRAWAL"); Instant now=Instant.now(),exp=now.plusSeconds(PROOF_SECONDS);
        String token=Jwts.builder().setHeaderParam("typ","JWT").setIssuer("identity").setSubject(Long.toString(userId)).claim("typ","mpin-proof").claim("act",staff).claim("purpose","WITHDRAWAL").claim("bind",hashBinding(binding("WITHDRAWAL",fields))).claim("jti",UUID.randomUUID().toString()).setIssuedAt(Date.from(now)).setExpiration(Date.from(exp)).signWith(Keys.hmacShaKeyFor(proofKey),SignatureAlgorithm.HS256).compact();
        return new Verification(Map.of(),token,exp);
    }

    public static String binding(String purpose,Map<String,Object> values) {
        List<String> fields=FIELDS.get(purpose); if(fields==null||!values.keySet().equals(Set.copyOf(fields)))throw new IllegalArgumentException("Invalid MPIN binding fields");
        StringBuilder out=new StringBuilder(purpose);
        for(String f:fields){Object v=values.get(f);if(v==null)throw new IllegalArgumentException("Missing binding field");String value=v.toString();if(v instanceof java.math.BigDecimal d)value=d.stripTrailingZeros().toPlainString();else if(v instanceof Number)value=new java.math.BigDecimal(value).stripTrailingZeros().toPlainString();else if(f.equals("toAccountNumber")||f.toLowerCase(Locale.ROOT).contains("currencycode"))value=value.trim();out.append('|').append(value);}
        return out.toString();
    }
    public static String hashBinding(String value) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);} }
    public static byte[] deriveProofKey(String jwtSecretBase64) { return hmac(Decoders.BASE64.decode(jwtSecretBase64),"mpin-proof-v1"); }

    private Attempt checkLockedRow(long userId,String actor,String pin,String purpose) {
        Map<String,Object> row=jdbc.query("SELECT cmp_hash,cmp_failed_attempts,cmp_locked_until FROM customer_mpin WHERE cmp_user_id=? FOR UPDATE",rs->rs.next()?Map.of("hash",rs.getString(1),"attempts",rs.getInt(2),"locked",rs.getTimestamp(3)==null?"":rs.getTimestamp(3).toInstant()):null,userId);
        if(row==null)return new Attempt("MPIN_NOT_SET",0,null);
        Instant locked="".equals(row.get("locked"))?null:(Instant)row.get("locked");if(locked!=null&&locked.isAfter(Instant.now()))return new Attempt("MPIN_LOCKED",0,locked);
        byte[] proposed=digest(userId,pin); boolean ok=false;
        try { ok=bcrypt.matches(HexFormat.of().formatHex(proposed),unprotect((String)row.get("hash"))); } catch(RuntimeException ignored) { }
        if(ok){jdbc.update("UPDATE customer_mpin SET cmp_failed_attempts=0,cmp_locked_until=NULL,cmp_last_failed_at=NULL WHERE cmp_user_id=?",userId);return new Attempt("OK",maxAttempts,null);}
        int attempts=((Number)row.get("attempts")).intValue()+1;boolean lock=attempts>=maxAttempts; jdbc.update("UPDATE customer_mpin SET cmp_failed_attempts=?,cmp_locked_until=?,cmp_last_failed_at=UTC_TIMESTAMP(6) WHERE cmp_user_id=?",attempts,lock?java.sql.Timestamp.from(Instant.now().plusSeconds(lockMinutes*60L)):null,userId);
        event(userId,"VERIFY_FAIL",actor,purpose);if(lock)event(userId,"LOCKED",actor,purpose);
        return lock?new Attempt("MPIN_LOCKED",0,Instant.now().plusSeconds(lockMinutes*60L)):new Attempt("MPIN_INVALID",maxAttempts-attempts,null);
    }
    private void event(long userId,String type,String actor,String purpose){jdbc.update("INSERT INTO customer_mpin_events(user_id,event_type,actor_username,purpose) VALUES(?,?,?,?)",userId,type,actor,purpose);}
    private static void validatePin(String pin){if(pin==null||!pin.matches("[0-9]{6}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"MPIN must contain exactly six digits.");if(weak(pin))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a less predictable MPIN.");}
    public static boolean weak(String p){if(p==null||!p.matches("[0-9]{6}"))return true;if(p.chars().allMatch(c->c==p.charAt(0)))return true;boolean asc=true,desc=true;for(int i=1;i<6;i++){asc&=p.charAt(i)==p.charAt(i-1)+1;desc&=p.charAt(i)==p.charAt(i-1)-1;}return asc||desc||p.substring(0,3).equals(p.substring(3))||p.substring(0,2).equals(p.substring(2,4))&&p.substring(2,4).equals(p.substring(4));}
    private byte[] digest(long userId,String pin){return hmac(pepper,userId+":"+String.valueOf(pin));}
    private String protect(String value){try{byte[] iv=new byte[12];random.nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(atRestKey,"AES"),new GCMParameterSpec(128,iv));byte[] encrypted=c.doFinal(value.getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length+encrypted.length).put(iv).put(encrypted).array());}catch(Exception e){throw new IllegalStateException("Unable to protect MPIN data");}}
    private String unprotect(String value){try{byte[] bytes=Base64.getDecoder().decode(value),iv=java.util.Arrays.copyOfRange(bytes,0,12),enc=java.util.Arrays.copyOfRange(bytes,12,bytes.length);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(atRestKey,"AES"),new GCMParameterSpec(128,iv));return new String(c.doFinal(enc),StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("Unable to read MPIN data");}}
    private static byte[] hmac(byte[] key,String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
}
