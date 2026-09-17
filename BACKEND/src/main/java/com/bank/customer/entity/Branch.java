package com.bank.customer.entity;

import jakarta.persistence.*;

@Entity @Table(name = "branches", schema = "public")
public class Branch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "bra_branch_id") private Long id;
    @Column(name = "bra_branch_code", nullable = false, unique = true) private String code;
    @Column(name = "bra_branch_name", nullable = false) private String name;
    @Column(name = "bra_city") private String city;
    @Column(name = "bra_is_active", nullable = false) private boolean active = true;
    public Long getId(){return id;} public String getCode(){return code;} public void setCode(String v){code=v;}
    public String getName(){return name;} public void setName(String v){name=v;} public String getCity(){return city;} public void setCity(String v){city=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}
