package com.bank.security.dto;
import jakarta.validation.constraints.*;
public class AuthRegisterRequest {
    @NotBlank @Size(min=3,max=255) private String username;
    @NotBlank @Size(min=8,max=100) private String password;
    @NotBlank @Email private String email;
    @NotBlank private String fullName;
    private String phone; private String address; private String legalEntityType;
    public String getUsername(){return username;} public void setUsername(String v){username=v;} public String getPassword(){return password;} public void setPassword(String v){password=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;} public String getFullName(){return fullName;} public void setFullName(String v){fullName=v;}
    public String getPhone(){return phone;} public void setPhone(String v){phone=v;} public String getAddress(){return address;} public void setAddress(String v){address=v;}
    public String getLegalEntityType(){return legalEntityType;} public void setLegalEntityType(String v){legalEntityType=v;}
}