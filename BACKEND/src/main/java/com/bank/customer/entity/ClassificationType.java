package com.bank.customer.entity;

import jakarta.persistence.*;

@Entity @Table(name = "customer_classification_types", schema = "public")
public class ClassificationType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name="cct_type_id") private Long id;
    @Column(name="cct_type_code", nullable=false, unique=true) private String code;
    @Column(name="cct_type_name", nullable=false) private String name;
    @Column(name="cct_is_active", nullable=false) private boolean active=true;
    public Long getId(){return id;} public String getCode(){return code;} public void setCode(String v){code=v;}
    public String getName(){return name;} public void setName(String v){name=v;} public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}
