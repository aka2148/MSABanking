package com.bank.security.service;

import com.bank.security.jwt.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {
    private final JwtTokenProvider tokenProvider;
    private final long refreshLifetimeSeconds;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> refreshToSession = new ConcurrentHashMap<>();

    public SessionService(JwtTokenProvider tokenProvider,
                          @Value("${app.jwt.refresh-expiration-seconds:2592000}") long refreshLifetimeSeconds) {
        this.tokenProvider = tokenProvider;
        this.refreshLifetimeSeconds = refreshLifetimeSeconds;
    }

    public Session issue(String userId, String username, List<String> roles) {
        String sessionId = UUID.randomUUID().toString();
        String refreshToken = UUID.randomUUID().toString() + "." + UUID.randomUUID();
        Session session = new Session(sessionId, userId, username, List.copyOf(roles), refreshToken,
                Instant.now().plusSeconds(refreshLifetimeSeconds), false);
        sessions.put(sessionId, session);
        refreshToSession.put(refreshToken, sessionId);
        return session;
    }

    public String accessToken(Session session) {
        return tokenProvider.generateSessionToken(session.userId(), session.username(), session.roles(), session.sessionId());
    }

    public Session fromAccessToken(String accessToken) {
        if (!tokenProvider.validateToken(accessToken)) {
            return null;
        }
        Session session = sessions.get(tokenProvider.getSessionIdFromJwt(accessToken));
        return session == null || session.revoked() ? null : session;
    }

    public Session refresh(String refreshToken) {
        String sessionId = refreshToSession.get(refreshToken);
        Session old = sessionId == null ? null : sessions.get(sessionId);
        if (old == null || old.revoked() || old.refreshExpiresAt().isBefore(Instant.now())) {
            return null;
        }
        revoke(sessionId);
        return issue(old.userId(), old.username(), old.roles());
    }

    public void revoke(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session != null) {
            sessions.put(sessionId, new Session(session.sessionId(), session.userId(), session.username(),
                    session.roles(), session.refreshToken(), session.refreshExpiresAt(), true));
        }
    }

    public record Session(String sessionId, String userId, String username, List<String> roles,
                          String refreshToken, Instant refreshExpiresAt, boolean revoked) {}
}