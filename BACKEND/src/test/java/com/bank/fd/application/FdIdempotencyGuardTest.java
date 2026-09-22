package com.bank.fd.application;

import com.bank.fd.entity.FdOperationIdempotency;
import com.bank.fd.exception.FdDuplicateOperationException;
import com.bank.fd.repository.FdOperationIdempotencyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit proof for {@link FdIdempotencyGuard} replay/mismatch semantics.
 * No Spring context, no Docker — the repository is mocked.
 */
@ExtendWith(MockitoExtension.class)
class FdIdempotencyGuardTest {

    @Mock
    private FdOperationIdempotencyRepository repository;

    @InjectMocks
    private FdIdempotencyGuard guard;

    @Test
    void nullKeyReturnsNullWithoutSaving() {
        FdOperationIdempotency result = guard.claim("FUNDING", "fd-user-7", null, "hash-1");

        assertThat(result).isNull();
        verify(repository, never()).findByOperationScopeAndActorKeyAndIdempotencyKey(any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void blankKeyReturnsNullWithoutSaving() {
        FdOperationIdempotency result = guard.claim("FUNDING", "fd-user-7", "   ", "hash-1");

        assertThat(result).isNull();
        verify(repository, never()).findByOperationScopeAndActorKeyAndIdempotencyKey(any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void unknownKeyPersistsPendingRowWithGivenHash() {
        when(repository.findByOperationScopeAndActorKeyAndIdempotencyKey("BOOKING", "fd-user-7", "key-1"))
                .thenReturn(Optional.empty());
        when(repository.save(any(FdOperationIdempotency.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String hash = FdIdempotencyGuard.sha256Hex("canonical-body");
        FdOperationIdempotency claim = guard.claim("BOOKING", "fd-user-7", "key-1", hash);

        assertThat(claim).isNotNull();
        assertThat(claim.getOperationScope()).isEqualTo("BOOKING");
        assertThat(claim.getActorKey()).isEqualTo("fd-user-7");
        assertThat(claim.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(claim.getRequestHash()).isEqualTo(hash);
        assertThat(claim.getStatus()).isEqualTo("PENDING");
        ArgumentCaptor<FdOperationIdempotency> saved = ArgumentCaptor.forClass(FdOperationIdempotency.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getRequestHash()).isEqualTo(hash);
    }

    @Test
    void sameKeyAndHashOnCompletedClaimReturnsClaimWithoutSaving() {
        FdOperationIdempotency stored = new FdOperationIdempotency();
        stored.setOperationScope("FUNDING");
        stored.setActorKey("fd-user-7");
        stored.setIdempotencyKey("key-2");
        stored.setRequestHash("hash-abc");
        stored.setStatus("COMPLETED");
        stored.setAggregateId(42L);
        stored.setResponseReference("fd-42");
        when(repository.findByOperationScopeAndActorKeyAndIdempotencyKey("FUNDING", "fd-user-7", "key-2"))
                .thenReturn(Optional.of(stored));

        FdOperationIdempotency replay = guard.claim("FUNDING", "fd-user-7", "key-2", "hash-abc");

        assertThat(replay).isSameAs(stored);
        verify(repository, never()).save(any());
    }

    @Test
    void sameKeyWithDifferentHashThrowsDuplicateReferencingStoredRecord() {
        FdOperationIdempotency stored = new FdOperationIdempotency();
        stored.setOperationScope("MATURITY");
        stored.setActorKey("fd-user-7");
        stored.setIdempotencyKey("key-3");
        stored.setRequestHash("original-hash");
        stored.setStatus("COMPLETED");
        stored.setResponseReference("maturity-txn-99");
        when(repository.findByOperationScopeAndActorKeyAndIdempotencyKey("MATURITY", "fd-user-7", "key-3"))
                .thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> guard.claim("MATURITY", "fd-user-7", "key-3", "different-hash"))
                .isInstanceOf(FdDuplicateOperationException.class)
                .hasMessageContaining("maturity-txn-99");
        verify(repository, never()).save(any());
    }

    @Test
    void sha256HexIsDeterministicAndProduces64HexChars() {
        String first = FdIdempotencyGuard.sha256Hex("canonical-body");
        String second = FdIdempotencyGuard.sha256Hex("canonical-body");
        String other = FdIdempotencyGuard.sha256Hex("other-body");

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
        assertThat(first).matches("[0-9a-f]+");
        assertThat(other).isNotEqualTo(first);
    }
}
