package com.bank.fd.application;

import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdUserReference;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Ownership check for FD accounts: holder match passes, staff bypass passes,
 * otherwise the FD is hidden behind a 404 so existence is not leaked.
 */
@Service
public class FdAccessPolicy {

    private final FdActorResolver actorResolver;

    public FdAccessPolicy(FdActorResolver actorResolver) {
        this.actorResolver = actorResolver;
    }

    public void requireOwned(
            String username, FdAccount account, Collection<? extends GrantedAuthority> authorities) {
        FdUserReference holder = actorResolver.resolve(username);
        if (account != null
                && holder != null
                && holder.getId() != null
                && holder.getId().equals(account.getPrimaryHolderId())) {
            return;
        }
        if (authorities != null) {
            for (GrantedAuthority authority : authorities) {
                if (authority == null || authority.getAuthority() == null) {
                    continue;
                }
                String role = authority.getAuthority();
                if ("ROLE_BANK_OFFICER".equals(role) || "ROLE_ADMIN".equals(role)) {
                    return;
                }
            }
        }
        throw new ResourceNotFoundException("FD account not found");
    }

    public void requireOwned(String username, FdAccount account, Authentication authentication) {
        Collection<? extends GrantedAuthority> authorities =
                authentication == null ? null : authentication.getAuthorities();
        requireOwned(username, account, authorities);
    }
}
