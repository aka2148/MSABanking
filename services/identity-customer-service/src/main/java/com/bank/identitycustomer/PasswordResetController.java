package com.bank.identitycustomer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth/password")
public class PasswordResetController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;

    public PasswordResetController(JdbcTemplate jdbc, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    @PostMapping("/change") @Transactional
    public void change(Authentication authentication,@Valid @RequestBody ChangeRequest request){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT ucr_user_id,ucr_password_hash FROM user_credentials WHERE ucr_username=?",authentication.getName());
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found");
        Map<String,Object> row=rows.get(0);long userId=((Number)row.get("ucr_user_id")).longValue();String current=(String)row.get("ucr_password_hash");
        if(!passwords.matches(request.currentPassword,current))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Current password is incorrect");
        if(!PasswordPolicy.isStrong(request.newPassword,authentication.getName()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,PasswordPolicy.ERROR_MESSAGE);
        if(passwords.matches(request.newPassword,current)||jdbc.queryForList("SELECT uph_password_hash FROM user_password_history WHERE uph_user_id=? ORDER BY uph_changed_at DESC LIMIT 5",String.class,userId).stream().anyMatch(old->passwords.matches(request.newPassword,old)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a password you have not used before");
        jdbc.update("INSERT INTO user_password_history(uph_user_id,uph_password_hash) VALUES(?,?)",userId,current);
        jdbc.update("UPDATE user_credentials SET ucr_password_hash=?,ucr_password_changed_at=UTC_TIMESTAMP(6),ucr_must_change_password=0,ucr_token_version=ucr_token_version+1 WHERE ucr_user_id=?",passwords.encode(request.newPassword),userId);
    }

    public record ChangeRequest(@NotBlank String currentPassword,@NotBlank @Size(min=12,max=100) String newPassword){
        @Override public String toString() { return "ChangeRequest[redacted]"; }
    }
}
