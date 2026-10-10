package com.bank.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;

import java.time.LocalDate;

/** Request body for POST /employees and PUT /employees/{id}. Prevents direct entity deserialization. */
public class EmployeeProfileRequest {
    @NotBlank private String employeeCode;
    @NotBlank private String fullName;
    @Email private String email;
    /** User ID that this employee profile is linked to. Required for creation. */
    private Long userId;
    private Integer homeBranchId;
    private String designation;
    private LocalDate dateOfJoining;
    private LocalDate dateOfLeaving;

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String v) { employeeCode = v; }
    public String getFullName() { return fullName; }
    public void setFullName(String v) { fullName = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { email = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { userId = v; }
    public Integer getHomeBranchId() { return homeBranchId; }
    public void setHomeBranchId(Integer v) { homeBranchId = v; }
    public String getDesignation() { return designation; }
    public void setDesignation(String v) { designation = v; }
    public LocalDate getDateOfJoining() { return dateOfJoining; }
    public void setDateOfJoining(LocalDate v) { dateOfJoining = v; }
    public LocalDate getDateOfLeaving() { return dateOfLeaving; }
    public void setDateOfLeaving(LocalDate v) { dateOfLeaving = v; }
}
