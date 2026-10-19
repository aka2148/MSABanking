package com.bank.identitycustomer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth/password")
public class PasswordResetController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final RestClient mailApi = RestClient.create();
    private final boolean enabled;
    private final String from;
    private final String resetUrl;
    private final String mailApiUrl;
    private final String mailApiKey;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetController(JdbcTemplate jdbc, PasswordEncoder passwords,
            @Value("${app.auth.password-reset-enabled:false}") boolean enabled,
            @Value("${app.auth.password-reset-from:no-reply@localhost}") String from,
            @Value("${app.auth.password-reset-url:http://localhost:4200/reset-password?token=}") String resetUrl,
            @Value("${app.auth.password-reset-api-url:https://api.resend.com/emails}") String mailApiUrl,
            @Value("${app.auth.password-reset-api-key:}") String mailApiKey) {
        this.jdbc=jdbc; this.passwords=passwords; this.enabled=enabled; this.from=from; this.resetUrl=resetUrl; this.mailApiUrl=mailApiUrl; this.mailApiKey=mailApiKey;
    }

    @PostMapping("/forgot") @Transactional
    public Accepted forgot(@Valid @RequestBody ForgotRequest request) {
        requireDelivery();
        List<Map<String,Object>> users=jdbc.queryForList("SELECT u.usr_user_id,e.cem_email_address FROM user_credentials c JOIN users u ON u.usr_user_id=c.ucr_user_id JOIN customers cu ON cu.cus_user_id=u.usr_user_id JOIN customer_emails e ON e.cem_customer_id=cu.cus_customer_id AND e.cem_is_primary=1 WHERE c.ucr_username=?", request.username);
        if (!users.isEmpty()) {
            long userId=((Number)users.get(0).get("usr_user_id")).longValue(); String email=(String)users.get(0).get("cem_email_address");
            byte[] bytes=new byte[32]; random.nextBytes(bytes); String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            jdbc.update("DELETE FROM password_reset_tokens WHERE prt_user_id=?", userId);
            jdbc.update("INSERT INTO password_reset_tokens(prt_user_id,prt_token_hash,prt_expires_at) VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 30 MINUTE))",userId,hash(token));
            try {
                String body="A password reset was requested for your MoneyPal account. Use this one-time link within 30 minutes:\n\n"+resetUrl+java.net.URLEncoder.encode(token, StandardCharsets.UTF_8)+"\n\nIf you did not request this, ignore this message.";
                mailApi.post().uri(mailApiUrl).header("Authorization","Bearer "+mailApiKey).body(Map.of("from",from,"to",email,"subject","MoneyPal password reset","text",body)).retrieve().toBodilessEntity();
            } catch (RuntimeException ex) {
                jdbc.update("DELETE FROM password_reset_tokens WHERE prt_user_id=?", userId);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Password reset email could not be delivered");
            }
        }
        return new Accepted("If the account is eligible, password reset instructions have been sent.");
    }

    @PostMapping("/reset") @Transactional
    public void reset(@Valid @RequestBody ResetRequest request) {
        requireDelivery();
        List<Map<String,Object>> matches=jdbc.queryForList("SELECT t.prt_id,t.prt_user_id,c.ucr_password_hash FROM password_reset_tokens t JOIN user_credentials c ON c.ucr_user_id=t.prt_user_id WHERE t.prt_token_hash=? AND t.prt_used_at IS NULL AND t.prt_expires_at>UTC_TIMESTAMP(6)",hash(request.token));
        if(matches.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Reset token is invalid or expired");
        Map<String,Object> row=matches.get(0); long userId=((Number)row.get("prt_user_id")).longValue();
        String current=(String)row.get("ucr_password_hash");
        if(passwords.matches(request.newPassword,current)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a password you have not used before");
        List<String> prior=jdbc.queryForList("SELECT uph_password_hash FROM user_password_history WHERE uph_user_id=? ORDER BY uph_changed_at DESC LIMIT 5",String.class,userId);
        if(prior.stream().anyMatch(hash->passwords.matches(request.newPassword,hash))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a password you have not used before");
        jdbc.update("INSERT INTO user_password_history(uph_user_id,uph_password_hash) VALUES(?,?)",userId,current);
        jdbc.update("UPDATE user_credentials SET ucr_password_hash=?,ucr_password_changed_at=UTC_TIMESTAMP(6),ucr_must_change_password=0,ucr_failed_attempt_count=0,ucr_locked_until=NULL,ucr_token_version=ucr_token_version+1 WHERE ucr_user_id=?",passwords.encode(request.newPassword),userId);
        jdbc.update("UPDATE password_reset_tokens SET prt_used_at=UTC_TIMESTAMP(6) WHERE prt_id=?",row.get("prt_id"));
        jdbc.update("DELETE FROM password_reset_tokens WHERE prt_user_id=? AND prt_id<>?",userId,row.get("prt_id"));
    }

    @PostMapping("/change") @Transactional
    public void change(Authentication authentication,@Valid @RequestBody ChangeRequest request){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT ucr_user_id,ucr_password_hash FROM user_credentials WHERE ucr_username=?",authentication.getName());
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found");
        Map<String,Object> row=rows.get(0);long userId=((Number)row.get("ucr_user_id")).longValue();String current=(String)row.get("ucr_password_hash");
        if(!passwords.matches(request.currentPassword,current))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Current password is incorrect");
        if(passwords.matches(request.newPassword,current)||jdbc.queryForList("SELECT uph_password_hash FROM user_password_history WHERE uph_user_id=? ORDER BY uph_changed_at DESC LIMIT 5",String.class,userId).stream().anyMatch(old->passwords.matches(request.newPassword,old)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a password you have not used before");
        jdbc.update("INSERT INTO user_password_history(uph_user_id,uph_password_hash) VALUES(?,?)",userId,current);
        jdbc.update("UPDATE user_credentials SET ucr_password_hash=?,ucr_password_changed_at=UTC_TIMESTAMP(6),ucr_must_change_password=0,ucr_token_version=ucr_token_version+1 WHERE ucr_user_id=?",passwords.encode(request.newPassword),userId);
    }

    private void requireDelivery(){if(!enabled||mailApiKey==null||mailApiKey.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Password reset email delivery is not configured");}
    private String hash(String token){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("Unable to hash reset token",e);}}
    public record ForgotRequest(@NotBlank @Size(max=255) String username){}
    public record ResetRequest(@NotBlank @Size(max=128) String token,@NotBlank @Size(min=8,max=100) String newPassword){}
    public record ChangeRequest(@NotBlank String currentPassword,@NotBlank @Size(min=8,max=100) String newPassword){}
    public record Accepted(String message){}
}
