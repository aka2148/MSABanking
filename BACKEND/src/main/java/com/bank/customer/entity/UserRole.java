package com.bank.customer.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_roles", schema = "public", uniqueConstraints = @UniqueConstraint(columnNames = {"ur_user_id", "ur_role_id"}))
public class UserRole {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ur_user_role_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ur_user_id", nullable = false) private User user;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ur_role_id", nullable = false) private BankRole role;

    public void setUser(User user) { this.user = user; }
    public void setRole(BankRole role) { this.role = role; }
    public BankRole getRole() { return role; }
}