package com.bank.security.dto;
import jakarta.validation.constraints.NotBlank;
public class ChangePasswordRequest { @NotBlank private String currentPassword; @NotBlank private String newPassword;
 public String getCurrentPassword(){return currentPassword;} public void setCurrentPassword(String v){currentPassword=v;} public String getNewPassword(){return newPassword;} public void setNewPassword(String v){newPassword=v;} }