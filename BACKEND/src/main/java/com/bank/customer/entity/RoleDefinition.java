package com.bank.customer.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "roles", schema = "public")
public class RoleDefinition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "rol_role_id") private Integer id;
    @Column(name = "rol_role_code", nullable = false, unique = true, length = 50) private String code;
    @Column(name = "rol_role_name", nullable = false, length = 255) private String name;
    @Column(name = "rol_description", length = 500) private String description;
    public Integer getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}