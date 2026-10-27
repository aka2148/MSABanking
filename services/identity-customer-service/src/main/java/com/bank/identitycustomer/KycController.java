package com.bank.identitycustomer;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/customers/me/kyc")
@PreAuthorize("hasRole('CUSTOMER')")
public class KycController {
    private final KycService service;
    private final MpinService mpin; private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public KycController(KycService service,MpinService mpin,org.springframework.jdbc.core.JdbcTemplate jdbc) { this.service = service; this.mpin=mpin;this.jdbc=jdbc; }
    @GetMapping public Map<String, Object> get(Authentication auth) { return service.get(auth.getName()); }
    @PutMapping("/draft") public Map<String, Object> save(Authentication auth, @RequestBody DraftRequest body) { return service.save(auth.getName(), body); }
    @PostMapping("/submit") public Map<String, Object> submit(Authentication auth) { return service.submit(auth.getName()); }
    // Spring debug request logging must not render government ID values.
    public static class DraftRequest extends java.util.LinkedHashMap<String, Object> {
        @Override public String toString() { return "KYC draft [redacted]"; }
    }
    @PostMapping(value="/document", consumes="multipart/form-data")
    public Map<String, Object> upload(Authentication auth, @RequestParam("file") MultipartFile file) { return service.document(auth.getName(), file); }
    @DeleteMapping("/document") public Map<String, Object> remove(Authentication auth) { return service.document(auth.getName(), null); }
    @PostMapping("/rekyc") public org.springframework.http.ResponseEntity<?> rekyc(Authentication auth, @RequestBody RekycRequest request) {
        Long user=jdbc.query("SELECT ucr_user_id FROM user_credentials WHERE ucr_username=?",rs->rs.next()?rs.getLong(1):null,auth.getName());
        if(user==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication is invalid.");
        MpinService.Attempt check=mpin.rekycVerify(user,auth.getName(),request.mpin());
        if(!check.ok()){Map<String,Object> body=new java.util.LinkedHashMap<>();body.put("code",check.code());if("MPIN_INVALID".equals(check.code()))body.put("attemptsRemaining",check.attemptsRemaining());if(check.lockedUntil()!=null)body.put("lockedUntil",check.lockedUntil());return org.springframework.http.ResponseEntity.status(switch(check.code()){case "MPIN_INVALID"->HttpStatus.FORBIDDEN;case "MPIN_LOCKED"->HttpStatus.LOCKED;default->HttpStatus.CONFLICT;}).body(body);}
        return org.springframework.http.ResponseEntity.ok(service.rekyc(auth.getName(),request.remarks()));
    }
    @PostMapping("/reapply")
    public Map<String,Object> reapply(Authentication auth,@RequestHeader(value="X-MPIN-Proof",required=false) String proof,@RequestBody ReapplyRequest request) {
        return service.reapply(auth.getName(),request.note(),proof);
    }
    public record RekycRequest(String remarks,String mpin) { @Override public String toString(){return "RekycRequest[remarks=[redacted], mpin=[redacted]]";} }
    public record ReapplyRequest(String note) { @Override public String toString(){return "ReapplyRequest[note=[redacted]]";} }
    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> error(ResponseStatusException ex) {
        String reason=ex.getReason()==null?"Unable to save KYC.":ex.getReason();
        if(reason.startsWith("MPIN_")) return org.springframework.http.ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",reason,"code",reason));
        return org.springframework.http.ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", reason));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, String> tooLarge() { return Map.of("message", "Choose a non-empty document up to 5 MB."); }
}
