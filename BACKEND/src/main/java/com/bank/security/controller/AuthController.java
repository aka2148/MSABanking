package com.bank.security.controller;

import com.bank.common.exception.InvalidInputException;
import com.bank.customer.entity.CustomerCategory;
import com.bank.customer.entity.CustomerAddress;
import com.bank.customer.entity.CustomerDetail;
import com.bank.customer.entity.CustomerEmail;
import com.bank.customer.entity.CustomerPhoneNumber;
import com.bank.customer.entity.BankRole;
import com.bank.customer.entity.UserRole;
import com.bank.customer.entity.CustomerProfile;
import com.bank.customer.entity.Role;
import com.bank.customer.entity.User;
import com.bank.customer.repository.CustomerProfileRepository;
import com.bank.customer.repository.BankRoleRepository;
import com.bank.customer.repository.UserRepository;
import com.bank.security.dto.JwtResponse;
import com.bank.security.dto.LoginRequest;
import com.bank.security.dto.RegisterRequest;
import com.bank.security.jwt.JwtTokenProvider;
import com.bank.security.service.LoginAttemptService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    @Autowired
    private BankRoleRepository bankRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest,
                                                        HttpServletRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));
        } catch (AuthenticationException ex) {
            loginAttemptService.recordFailure(loginRequest.getUsername());
            throw ex;
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        loginAttemptService.recordSuccess(loginRequest.getUsername(), request.getRemoteAddr());
        String jwt = jwtTokenProvider.generateToken(authentication);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByCredentialsUsername(userDetails.getUsername()).orElseThrow();

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new JwtResponse(jwt, userDetails.getUsername(), user.getEmail(), roles));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        if (userRepository.existsByCredentialsUsername(registerRequest.getUsername())) {
            throw new InvalidInputException("Error: Username is already taken!");
        }

        Role role = registerRequest.getRole() != null ? registerRequest.getRole() : Role.ROLE_CUSTOMER;

        BankRole bankRole = bankRoleRepository.findByCode(role.name()).orElseGet(() -> {
            BankRole createdRole = new BankRole();
            createdRole.setCode(role.name());
            createdRole.setName(role.name().replace("ROLE_", "").replace('_', ' '));
            createdRole.setDescription("Application role " + role.name());
            return bankRoleRepository.save(createdRole);
        });

        User user = User.builder()
                .username(registerRequest.getUsername())
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .role(role)
                .build();

            UserRole userRole = new UserRole();
            userRole.setRole(bankRole);
            user.addRoleAssignment(userRole);

        CustomerCategory category = registerRequest.getCategory() != null ? registerRequest.getCategory() : CustomerCategory.STANDARD;

        CustomerProfile profile = CustomerProfile.builder()
                .user(user)
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .phone(registerRequest.getPhone())
                .address(registerRequest.getAddress())
                .category(category)
                .build();
        profile.setCustomerNumber("CUS-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());

        CustomerDetail detail = new CustomerDetail();
        detail.setFullName(registerRequest.getFirstName() + " " + registerRequest.getLastName());
        profile.addDetail(detail);

        CustomerEmail email = new CustomerEmail();
        email.setEmailAddress(registerRequest.getEmail());
        profile.addEmail(email);

        if (registerRequest.getPhone() != null && !registerRequest.getPhone().isBlank()) {
            CustomerPhoneNumber phone = new CustomerPhoneNumber();
            phone.setPhoneNumber(registerRequest.getPhone());
            profile.addPhoneNumber(phone);
        }

        if (registerRequest.getAddress() != null && !registerRequest.getAddress().isBlank()) {
            CustomerAddress address = new CustomerAddress();
            address.setCity(registerRequest.getAddress());
            profile.addAddress(address);
        }

        customerProfileRepository.save(profile);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "User registered successfully with profile!"
        ));
    }
}
