package com.bank.fd.application;

import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdUserReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Ownership matrix for {@link FdAccessPolicy}: holder match passes, staff
 * bypass passes, strangers get 404 without leaking existence.
 */
@ExtendWith(MockitoExtension.class)
class FdAccessPolicyTest {

    @Mock
    private FdActorResolver actorResolver;

    @InjectMocks
    private FdAccessPolicy policy;

    @Test
    void ownerPassesHolderMatch() {
        when(actorResolver.resolve("alice")).thenReturn(holder(7L));

        assertThatCode(() -> policy.requireOwned("alice", account(7L),
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .doesNotThrowAnyException();
    }

    @Test
    void strangerCustomerSeesNotFoundWithoutExistenceLeak() {
        when(actorResolver.resolve("mallory")).thenReturn(holder(9L));

        assertThatThrownBy(() -> policy.requireOwned("mallory", account(7L),
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("FD account not found");
    }

    @Test
    void bankOfficerPassesForNonOwnedAccount() {
        when(actorResolver.resolve("officer")).thenReturn(holder(9L));

        assertThatCode(() -> policy.requireOwned("officer", account(7L),
                List.of(new SimpleGrantedAuthority("ROLE_BANK_OFFICER"))))
                .doesNotThrowAnyException();
    }

    @Test
    void adminPassesForNonOwnedAccount() {
        when(actorResolver.resolve("admin")).thenReturn(holder(9L));

        assertThatCode(() -> policy.requireOwned("admin", account(7L),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .doesNotThrowAnyException();
    }

    @Test
    void customerWithNoAuthoritiesSeesNotFoundOnNonOwnedAccount() {
        when(actorResolver.resolve("mallory")).thenReturn(holder(9L));

        assertThatThrownBy(() -> policy.requireOwned("mallory", account(7L), List.of()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("FD account not found");
    }

    @Test
    void blankUsernamePropagatesResolverNotFound() {
        when(actorResolver.resolve("   "))
                .thenThrow(new ResourceNotFoundException("Authenticated customer is required"));

        assertThatThrownBy(() -> policy.requireOwned("   ", account(7L),
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Authenticated customer is required");
    }

    private static FdUserReference holder(Long id) {
        FdUserReference holder = new FdUserReference();
        holder.setId(id);
        return holder;
    }

    private static FdAccount account(Long primaryHolderId) {
        FdAccount account = new FdAccount();
        account.setPrimaryHolderId(primaryHolderId);
        return account;
    }
}
