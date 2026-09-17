package com.bank.customer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_password_history", schema = "public")
public class UserPasswordHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "uph_history_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uph_user_id", nullable = false)
    @JdbcTypeCode(SqlTypes.INTEGER)
    private User user;

    @Column(name = "uph_password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "uph_changed_at", nullable = false)
    private OffsetDateTime changedAt = OffsetDateTime.now();

    public void setUser(User user) { this.user = user; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getPasswordHash() { return passwordHash; }
}