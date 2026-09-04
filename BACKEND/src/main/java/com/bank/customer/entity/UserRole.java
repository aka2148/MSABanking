package com.bank.customer.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_roles", schema = "public", uniqueConstraints = @UniqueConstraint(columnNames = {"ur_user_id", "ur_role_id"}))
public class UserRole {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "ur_user_role_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ur_user_id", nullable = false) private User user;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "ur_role_id", nullable = false) private RoleDefinition role;
    @Column(name = "ur_assigned_date", nullable = false) private OffsetDateTime assignedDate = OffsetDateTime.now();
    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public RoleDefinition getRole() { return role; }
    public void setRole(RoleDefinition role) { this.role = role; }
    public String getRoleCode() { return role == null ? "ROLE_CUSTOMER" : role.getCode(); }
}