package com.bank.identitycustomer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/password")
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery;

    public PasswordRecoveryController(PasswordRecoveryService recovery) {
        this.recovery = recovery;
    }

    @PostMapping("/forgot")
    public ResponseEntity<PasswordRecoveryService.Accepted> forgot(@Valid @RequestBody ForgotRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(recovery.forgot(request.identifier()));
    }

    @PostMapping("/verify")
    public PasswordRecoveryService.VerifiedReset verify(@Valid @RequestBody VerifyRequest request) {
        return recovery.verify(request.identifier(), request.code());
    }

    @PostMapping("/reset")
    public AuthController.JwtResponse reset(@Valid @RequestBody ResetRequest request) {
        return recovery.reset(request.resetToken(), request.newPassword());
    }

    public record ForgotRequest(@NotBlank @Size(max = 255) String identifier) {
        @Override public String toString() { return "ForgotRequest[redacted]"; }
    }
    public record VerifyRequest(@NotBlank @Size(max = 255) String identifier,
                                @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
        @Override public String toString() { return "VerifyRequest[redacted]"; }
    }
    public record ResetRequest(@NotBlank @Size(max = 128) String resetToken,
                               @NotBlank @Size(min = 12, max = 100) String newPassword) {
        @Override public String toString() { return "ResetRequest[redacted]"; }
    }
}
