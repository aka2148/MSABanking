package com.bank.fdservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/savings-accounts")
public class SavingsTransferController {
    private final JdbcTemplate jdbc;
    private final SavingsTransferService transfers;
    private final MpinProofVerifier mpin;

    public SavingsTransferController(JdbcTemplate jdbc, SavingsTransferService transfers, MpinProofVerifier mpin) {
        this.jdbc = jdbc;
        this.transfers = transfers;
        this.mpin=mpin;
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('CUSTOMER')")
    public SavingsTransferService.TransferResponse transfer(Authentication actor,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max=255) String key,
            @RequestHeader(value="X-MPIN-Proof",required=false) String proof,
            @Valid @RequestBody TransferRequest request) {
        long userId = userId(actor);
        Integer owned = jdbc.query("SELECT 1 FROM savings_accounts WHERE sa_account_id=? AND sa_holder_id=?",
                rs -> rs.next() ? 1 : null, request.fromAccountId(), userId);
        if (owned == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Savings account not found");
        CustomerProductAccess.requireVerified(userId);
        validateRemark(request.remark());
        String hash = requestHash(request);
        SavingsTransferService.TransferResponse replay = transfers.replayIfPresent(userId, key, hash, request.fromAccountId());
        if (replay != null) return replay;
        String canonical=MpinProofVerifier.canonical("TRANSFER",request.fromAccountId(),request.toAccountNumber(),request.amount());
        String jti=mpin.verify(actor,proof,"TRANSFER",userId,canonical);
        return transfers.transfer(userId, actor.getName(), key, hash, request,jti);
    }

    static String requestHash(TransferRequest request) {
        BigDecimal canonicalAmount = request.amount() == null ? null : request.amount().stripTrailingZeros();
        String normalizedNumber = request.toAccountNumber() == null ? "" : request.toAccountNumber().trim();
        String canonical = request.fromAccountId() + "|" + normalizedNumber + "|"
                + (canonicalAmount == null ? "" : canonicalAmount.toPlainString()) + "|"
                + (request.remark() == null ? "" : request.remark().trim());
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static void validateRemark(String remark) {
        if (remark != null && (remark.length() > 140 || remark.matches("(?s).*[<>\\p{Cntrl}].*")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Remark must be at most 140 characters and contain no markup or control characters");
    }
    private static long userId(Authentication actor) {
        Object value = actor.getDetails() instanceof Map<?, ?> details ? details.get("userId") : null;
        if (!(value instanceof Number number) || number.longValue() <= 0)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token does not contain a valid customer identity");
        return number.longValue();
    }

    public record TransferRequest(@NotNull @Positive Long fromAccountId, @NotBlank @Size(max=64) String toAccountNumber,
                                  @NotNull @DecimalMin(value="0.00000001") BigDecimal amount,
                                  @Size(max=140) String remark) {}
}
