package com.bank.customer.entity;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

/** Maps public.users; credentials are intentionally stored in user_credentials. */
@Entity
@Table(name = "users", schema = "public")
public class User implements UserDetails {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "usr_user_id") private Long id;
    @Column(name = "usr_user_uuid", nullable = false, unique = true, length = 64) private String userUuid;
    @Column(name = "usr_user_type", nullable = false, length = 30) private String userType = "CUSTOMER";
    @Column(name = "usr_status", nullable = false, length = 30) private String status = "ACTIVE";
    @Column(name = "usr_uuid", nullable = false, length = 64) private String uuid;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private UserCredential credentials;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<UserRole> roleAssignments = new ArrayList<>();

    /* Legacy API compatibility. Role assignments belong in user_roles. */
    @Transient private Role role = Role.ROLE_CUSTOMER;
    @Transient private String email;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUserUuid() { return userUuid; }
    public void setUserUuid(String userUuid) { this.userUuid = userUuid; }
    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public UserCredential getCredentials() { return credentials; }
    public void setCredentials(UserCredential credentials) { this.credentials = credentials; if (credentials != null) credentials.setUser(this); }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public List<UserRole> getRoleAssignments() { return roleAssignments; }
    public void addRoleAssignment(UserRole roleAssignment) { roleAssignments.add(roleAssignment); roleAssignment.setUser(this); }

    @PrePersist
    void initializeIdentifiers() {
        if (userUuid == null) userUuid = java.util.UUID.randomUUID().toString();
        if (uuid == null) uuid = java.util.UUID.randomUUID().toString();
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        if (!roleAssignments.isEmpty()) {
            return roleAssignments.stream()
                    .map(UserRole::getRoleCode)
                    .map(SimpleGrantedAuthority::new)
                    .toList();
        }
        return List.of(new SimpleGrantedAuthority((role == null ? Role.ROLE_CUSTOMER : role).name()));
    }
    @Override public String getPassword() { return credentials == null ? null : credentials.getPasswordHash(); }
    @Override public String getUsername() { return credentials == null ? null : credentials.getUsername(); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return credentials == null || credentials.getLockedUntil() == null || credentials.getLockedUntil().isBefore(java.time.OffsetDateTime.now()); }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return "ACTIVE".equalsIgnoreCase(status); }

    public static UserBuilder builder() { return new UserBuilder(); }
    public static class UserBuilder {
        private final User user = new User();
        public UserBuilder id(Long id) { user.setId(id); return this; }
        public UserBuilder username(String username) { ensureCredentials().setUsername(username); return this; }
        public UserBuilder password(String password) { ensureCredentials().setPasswordHash(password); return this; }
        public UserBuilder email(String email) { user.setEmail(email); return this; }
        public UserBuilder role(Role role) { user.setRole(role); return this; }
        public User build() { return user; }
        private UserCredential ensureCredentials() { if (user.getCredentials() == null) user.setCredentials(new UserCredential()); return user.getCredentials(); }
    }
}
