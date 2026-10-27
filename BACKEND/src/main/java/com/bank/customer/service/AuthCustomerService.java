package com.bank.customer.service;
import com.bank.customer.dto.CustomerResponse; import com.bank.customer.entity.CustomerProfile; import com.bank.security.dto.*; import org.springframework.data.domain.Page; import org.springframework.security.core.Authentication;
public interface AuthCustomerService {
    JwtResponse register(AuthRegisterRequest request); JwtResponse login(LoginRequest request); JwtResponse refresh(Authentication authentication); void changePassword(String username, ChangePasswordRequest request); CustomerResponse me(String username);
    LoginIdentity authenticateForMfa(LoginRequest request); JwtResponse completeMfaLogin(String username);
    Page<CustomerResponse> customers(int page,int size,String status,String risk,String search); CustomerResponse getCustomer(Long id); CustomerResponse getCustomerByUserId(Long userId); CustomerProfile updateStatus(Long id,String status,String risk);
    record LoginIdentity(Long userId, String username, String email) {}
}
