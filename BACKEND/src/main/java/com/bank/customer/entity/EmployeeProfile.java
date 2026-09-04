package com.bank.customer.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity @Table(name="employee_profile", schema="public")
public class EmployeeProfile {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="emp_employee_profile_id") private Long id;
    @OneToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="emp_user_id") private User user;
    @Column(name="emp_employee_code", nullable=false, unique=true) private String employeeCode;
    @Column(name="emp_full_name", nullable=false) private String fullName;
    @Column(name="emp_home_branch_id") private Long homeBranchId;
    @Column(name="emp_designation") private String designation;
    @Column(name="emp_date_of_joining") private LocalDate dateOfJoining;
    @Column(name="emp_date_of_leaving") private LocalDate dateOfLeaving;
    public Long getId(){return id;} public User getUser(){return user;} public void setUser(User v){user=v;}
    public String getEmployeeCode(){return employeeCode;} public void setEmployeeCode(String v){employeeCode=v;} public String getFullName(){return fullName;} public void setFullName(String v){fullName=v;}
    public Long getHomeBranchId(){return homeBranchId;} public void setHomeBranchId(Long v){homeBranchId=v;} public String getDesignation(){return designation;} public void setDesignation(String v){designation=v;}
    public LocalDate getDateOfJoining(){return dateOfJoining;} public void setDateOfJoining(LocalDate v){dateOfJoining=v;} public LocalDate getDateOfLeaving(){return dateOfLeaving;} public void setDateOfLeaving(LocalDate v){dateOfLeaving=v;}
}