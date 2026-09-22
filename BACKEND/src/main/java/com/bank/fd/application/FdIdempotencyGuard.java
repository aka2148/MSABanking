package com.bank.fd.application;

import com.bank.fd.entity.FdOperationIdempotency;
import com.bank.fd.exception.FdDuplicateOperationException;
import com.bank.fd.repository.FdOperationIdempotencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Durable claim/replay guard over {@link FdOperationIdempotencyRepository}.
 *
 * <p>Callers compute the request hash with {@link #sha256Hex(String)} over the
 * canonical request JSON, and pass the resolved {@code fd_user_ref} identity as the
 * actor key ({@code "fd-user-" + id}), never a caller-supplied identifier.
 */
@Service
@Transactional
public class FdIdempotencyGuard {

    private final FdOperationIdempotencyRepository repository;

    public FdIdempotencyGuard(FdOperationIdempotencyRepository repository) {
        this.repository = repository;
    }

    /**
     * Claims an idempotency slot for the given scope/actor/key.
     *
     * @return null when the key is blank (caller falls back to natural duplicate
     *     detection); the existing claim when the same key+hash replays; otherwise the
     *     newly persisted PENDING claim.
     * @throws FdDuplicateOperationException when the same key arrives with a different body hash.
     */
    public FdOperationIdempotency claim(String scope, String actorKey, String key, String requestHash) {
        if (key == null || key.isBlank()) {
            return null;
        }
        Optional<FdOperationIdempotency> existing =
                repository.findByOperationScopeAndActorKeyAndIdempotencyKey(scope, actorKey, key);
        if (existing.isPresent()) {
            FdOperationIdempotency claim = existing.get();
            String storedHash = claim.getRequestHash();
            boolean sameHash = storedHash == null ? requestHash == null : storedHash.equals(requestHash);
            if (sameHash) {
                return claim;
            }
            String reference = claim.getResponseReference() != null
                    ? claim.getResponseReference()
                    : "aggregate:" + claim.getAggregateId();
            throw new FdDuplicateOperationException(scope, reference);
        }
        FdOperationIdempotency claim = new FdOperationIdempotency();
        claim.setOperationScope(scope);
        claim.setActorKey(actorKey);
        claim.setIdempotencyKey(key);
        claim.setRequestHash(requestHash);
        claim.setStatus("PENDING");
        claim.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return repository.save(claim);
    }

    /** Marks a claimed operation complete with its recorded result reference. */
    public void complete(FdOperationIdempotency claim, Long aggregateId, String responseReference) {
        if (claim == null) {
            return;
        }
        claim.setAggregateId(aggregateId);
        claim.setResponseReference(responseReference);
        claim.setStatus("COMPLETED");
        claim.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(claim);
    }

    /** SHA-256 hex of the canonical request JSON. */
    public static String sha256Hex(String canonicalJson) {
        String input = canonicalJson == null ? "" : canonicalJson;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
