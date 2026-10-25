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
    public KycController(KycService service) { this.service = service; }
    @GetMapping public Map<String, Object> get(Authentication auth) { return service.get(auth.getName()); }
    @PutMapping("/draft") public Map<String, Object> save(Authentication auth, @RequestBody DraftRequest body) { return service.save(auth.getName(), body); }
    // Spring debug request logging must not render government ID values.
    public static class DraftRequest extends java.util.LinkedHashMap<String, Object> {
        @Override public String toString() { return "KYC draft [redacted]"; }
    }
    @PostMapping(value="/document", consumes="multipart/form-data")
    public Map<String, Object> upload(Authentication auth, @RequestParam("file") MultipartFile file) { return service.document(auth.getName(), file); }
    @DeleteMapping("/document") public Map<String, Object> remove(Authentication auth) { return service.document(auth.getName(), null); }
    @PostMapping("/rekyc") public Map<String, Object> rekyc(Authentication auth, @RequestBody RekycRequest request) { return service.rekyc(auth.getName(), request.remarks()); }
    public record RekycRequest(String remarks) {}
    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> error(ResponseStatusException ex) {
        return org.springframework.http.ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", ex.getReason() == null ? "Unable to save KYC." : ex.getReason()));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, String> tooLarge() { return Map.of("message", "Choose a non-empty document up to 5 MB."); }
}
