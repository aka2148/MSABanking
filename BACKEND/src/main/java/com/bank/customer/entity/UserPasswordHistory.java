package com.bank.customer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JoinFormula;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_password_history", schema = "public")
public class UserPasswordHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "uph_history_id")
    private Integer id;

    @Column(name = "uph_user_id", nullable = false)
    private Integer userForeignKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinFormula(value = "cast(uph_user_id as bigint)", referencedColumnName = "usr_user_id")
    private User user;

    @Column(name = "uph_password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "uph_changed_at", nullable = false)
    private OffsetDateTime changedAt = OffsetDateTime.now();

    public void setUser(User user) {
        this.user = user;
        this.userForeignKey = user == null || user.getId() == null ? null : Math.toIntExact(user.getId());
    }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getPasswordHash() { return passwordHash; }
}