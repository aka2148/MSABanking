package com.bank.customer.dto;

import java.time.LocalDate;

public class CustomerDetailsDto {
    private String fullName;
    private LocalDate dateOfBirth;
    private String fatherOrSpouseName;
    private String occupation;
    public CustomerDetailsDto(){}
    public CustomerDetailsDto(String fullName, LocalDate dateOfBirth, String fatherOrSpouseName, String occupation){this.fullName=fullName;this.dateOfBirth=dateOfBirth;this.fatherOrSpouseName=fatherOrSpouseName;this.occupation=occupation;}
    public String getFullName(){return fullName;} public void setFullName(String value){fullName=value;}
    public LocalDate getDateOfBirth(){return dateOfBirth;} public void setDateOfBirth(LocalDate value){dateOfBirth=value;}
    public String getFatherOrSpouseName(){return fatherOrSpouseName;} public void setFatherOrSpouseName(String value){fatherOrSpouseName=value;}
    public String getOccupation(){return occupation;} public void setOccupation(String value){occupation=value;}
}