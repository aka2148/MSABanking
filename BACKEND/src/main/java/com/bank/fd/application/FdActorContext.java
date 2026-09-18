package com.bank.fd.application;

import java.util.Set;

/** Immutable actor boundary resolved from the local /api/auth identity. */
public record FdActorContext(
        String actorId,
        String actorType,
        Set<String> roles,
        boolean authenticated) {

    public FdActorContext {
        if (authenticated && (actorId == null || actorId.isBlank())) {
            throw new IllegalArgumentException("Authenticated FD actor must have an identifier");
        }
        if (actorType == null || actorType.isBlank()) {
            throw new IllegalArgumentException("FD actor type must not be blank");
        }
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
