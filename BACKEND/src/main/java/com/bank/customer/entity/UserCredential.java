package com.bank.customer.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Maps public.user_credentials. */
@Entity
@Table(name = "user_credentials", schema = "public")
public class UserCredential {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "ucr_credential_id") private Long id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ucr_user_id", nullable = false) private User user;
    @Column(name = "ucr_credential_type", nullable = false, length = 30) private String credentialType = "PASSWORD";
    @Column(name = "ucr_username", nullable = false, unique = true, length = 255) private String username;
    @Column(name = "ucr_password_hash", nullable = false, length = 255) private String passwordHash;
    @Column(name = "ucr_must_change_password", nullable = false) private boolean mustChangePassword;
    @Column(name = "ucr_failed_attempt_count", nullable = false) private int failedAttemptCount;
    @Column(name = "ucr_locked_until") private OffsetDateTime lockedUntil;
    @Column(name = "ucr_mfa_enabled", nullable = false) private boolean mfaEnabled;
    @Column(name = "ucr_password_changed_at") private OffsetDateTime passwordChangedAt;
    @Column(name = "ucr_last_login_at") private OffsetDateTime lastLoginAt;
    @Column(name = "ucr_last_login_ip", length = 64) private String lastLoginIp;
    @Column(name = "ucr_uuid", nullable = false, length = 64) private String uuid;
    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getCredentialType() { return credentialType; }
    public void setCredentialType(String credentialType) { this.credentialType = credentialType; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public boolean isMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }
    public int getFailedAttemptCount() { return failedAttemptCount; }
    public void setFailedAttemptCount(int failedAttemptCount) { this.failedAttemptCount = failedAttemptCount; }
    public OffsetDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(OffsetDateTime lockedUntil) { this.lockedUntil = lockedUntil; }
    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(OffsetDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public String getLastLoginIp() { return lastLoginIp; }
    public void setLastLoginIp(String lastLoginIp) { this.lastLoginIp = lastLoginIp; }

    @PrePersist
    void initializeUuid() { if (uuid == null) uuid = java.util.UUID.randomUUID().toString(); }
}
