package com.bank.customer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JoinFormula;
import java.time.LocalDate;

@Entity @Table(name="employee_profile", schema="public")
public class EmployeeProfile {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="emp_employee_profile_id") private Integer id;
    @Column(name="emp_user_id", nullable=false) private Integer userForeignKey;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinFormula(value="cast(emp_user_id as bigint)", referencedColumnName="usr_user_id") private User user;
    @Column(name="emp_employee_code", nullable=false, unique=true) private String employeeCode;
    @Column(name="emp_full_name", nullable=false) private String fullName;
    @Column(name="emp_email", length=255) private String email;
    @Column(name="emp_home_branch_id") private Integer homeBranchId;
    @Column(name="emp_designation") private String designation;
    @Column(name="emp_date_of_joining") private LocalDate dateOfJoining;
    @Column(name="emp_date_of_leaving") private LocalDate dateOfLeaving;
    public Integer getId(){return id;} public User getUser(){return user;} public void setUser(User v){user=v; userForeignKey=v==null||v.getId()==null?null:Math.toIntExact(v.getId());}
    public String getEmployeeCode(){return employeeCode;} public void setEmployeeCode(String v){employeeCode=v;} public String getFullName(){return fullName;} public void setFullName(String v){fullName=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public Integer getHomeBranchId(){return homeBranchId;} public void setHomeBranchId(Integer v){homeBranchId=v;} public String getDesignation(){return designation;} public void setDesignation(String v){designation=v;}
    public LocalDate getDateOfJoining(){return dateOfJoining;} public void setDateOfJoining(LocalDate v){dateOfJoining=v;} public LocalDate getDateOfLeaving(){return dateOfLeaving;} public void setDateOfLeaving(LocalDate v){dateOfLeaving=v;}
}
