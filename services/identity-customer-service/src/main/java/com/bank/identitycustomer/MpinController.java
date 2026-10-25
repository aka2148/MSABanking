package com.bank.identitycustomer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class MpinController {
    private final MpinService service; private final JdbcTemplate jdbc; private final PasswordEncoder passwords;
    public MpinController(MpinService service,JdbcTemplate jdbc,PasswordEncoder passwords){this.service=service;this.jdbc=jdbc;this.passwords=passwords;}

    @GetMapping("/auth/mpin/status") @PreAuthorize("hasRole('CUSTOMER')")
    public MpinService.Status status(Authentication auth){return service.status(userId(auth.getName()));}

    @PostMapping("/auth/mpin/setup") @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> setup(Authentication auth,@Valid @RequestBody SetupRequest r){service.setup(userId(auth.getName()),auth.getName(),r.password(),r.mpin(),passwords);return ResponseEntity.noContent().build();}

    @PostMapping("/auth/mpin/change") @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> change(Authentication auth,@Valid @RequestBody ChangeRequest r){return attempt(service.change(userId(auth.getName()),auth.getName(),r.currentMpin(),r.newMpin()));}

    @PostMapping("/auth/mpin/verify") @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> verify(Authentication auth,@Valid @RequestBody VerifyRequest r){
        MpinService.Verification result=service.verifyAndIssue(userId(auth.getName()),auth.getName(),r.mpin(),r.purpose(),r.fields());
        if(result.proofToken()==null)return failure(result.fields());
        return ResponseEntity.ok(Map.of("proofToken",result.proofToken(),"expiresAt",result.expiresAt()));
    }

    @PostMapping("/customers/{customerId}/mpin/verify") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public ResponseEntity<?> staffVerify(Authentication auth,@PathVariable long customerId,@Valid @RequestBody StaffVerifyRequest r){
        Long user=jdbc.query("SELECT cus_user_id FROM customers WHERE cus_customer_id=?",rs->rs.next()?rs.getLong(1):null,customerId);
        if(user==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Customer not found.");
        Map<String,Object> fields=new LinkedHashMap<>();fields.put("accountId",r.accountId());fields.put("amount",r.amount());
        MpinService.Verification result=service.staffVerify(user,auth.getName(),r.mpin(),fields);
        if(result.proofToken()==null)return failure(result.fields());
        return ResponseEntity.ok(Map.of("proofToken",result.proofToken(),"expiresAt",result.expiresAt()));
    }

    @PostMapping("/customers/{customerId}/mpin/reset") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public ResponseEntity<?> reset(Authentication auth,@PathVariable long customerId){Long user=jdbc.query("SELECT cus_user_id FROM customers WHERE cus_customer_id=?",rs->rs.next()?rs.getLong(1):null,customerId);if(user==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Customer not found.");service.reset(user,auth.getName());return ResponseEntity.noContent().build();}

    private ResponseEntity<?> attempt(MpinService.Attempt a){if(a.ok())return ResponseEntity.noContent().build();Map<String,Object> b=new LinkedHashMap<>();b.put("code",a.code());if("MPIN_INVALID".equals(a.code()))b.put("attemptsRemaining",a.attemptsRemaining());if(a.lockedUntil()!=null)b.put("lockedUntil",a.lockedUntil());return ResponseEntity.status(statusCode(a.code())).body(b);}
    private ResponseEntity<?> failure(Map<String,Object> fields){String code=(String)fields.get("code");Map<String,Object>b=new LinkedHashMap<>();b.put("code",code);if("MPIN_INVALID".equals(code))b.put("attemptsRemaining",fields.get("attemptsRemaining"));if("MPIN_LOCKED".equals(code))b.put("lockedUntil",fields.get("lockedUntil"));return ResponseEntity.status(statusCode(code)).body(b);}
    private HttpStatus statusCode(String code){return switch(code){case "MPIN_INVALID"->HttpStatus.FORBIDDEN;case "MPIN_LOCKED"->HttpStatus.LOCKED;case "MPIN_NOT_SET"->HttpStatus.CONFLICT;default->HttpStatus.BAD_REQUEST;};}
    private long userId(String username){Long id=jdbc.query("SELECT ucr_user_id FROM user_credentials WHERE ucr_username=?",rs->rs.next()?rs.getLong(1):null,username);if(id==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication is invalid.");return id;}
    public record SetupRequest(@NotBlank String password,@NotBlank String mpin){@Override public String toString(){return "SetupRequest[password=[redacted], mpin=[redacted]]";}}
    public record ChangeRequest(@NotBlank String currentMpin,@NotBlank String newMpin){@Override public String toString(){return "ChangeRequest[currentMpin=[redacted], newMpin=[redacted]]";}}
    public record VerifyRequest(@NotBlank String mpin,@NotBlank String purpose,Map<String,Object> fields){@Override public String toString(){return "VerifyRequest[mpin=[redacted], purpose="+purpose+"]";}}
    public record StaffVerifyRequest(@NotBlank String mpin,long accountId,@jakarta.validation.constraints.DecimalMin("0.01") java.math.BigDecimal amount){@Override public String toString(){return "StaffVerifyRequest[mpin=[redacted], accountId="+accountId+", amount=[redacted]]";}}
}
